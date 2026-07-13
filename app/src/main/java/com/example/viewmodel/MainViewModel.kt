package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.ClothingItem
import com.example.service.AuthState
import com.example.service.AuthService
import com.example.service.CatalogService
import com.example.service.OrderItem
import com.example.service.OrderService
import com.example.service.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLEncoder

/**
 * ViewModel principal gérant les états globaux de l'application Boutique Mode.
 * Orchestre l'authentification, le catalogue et les commandes avec gestion de simulation intégrée.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "MainViewModel"
    private val context = application.applicationContext

    // Services
    val authService = AuthService()
    
    // Détermination dynamique des services basés sur le mode simulation de AuthService
    val catalogService = CatalogService(authService.isSimulationMode)
    val orderService = OrderService(authService.isSimulationMode)

    // États du catalogue
    val clothingItems: StateFlow<List<ClothingItem>> = catalogService.clothingItems
    val orders: StateFlow<List<OrderItem>> = orderService.orders

    // États de navigation & filtres UI
    private val _currentScreen = MutableStateFlow<String>("login")
    val currentScreen: StateFlow<String> = _currentScreen

    private val _selectedClothingItem = MutableStateFlow<ClothingItem?>(null)
    val selectedClothingItem: StateFlow<ClothingItem?> = _selectedClothingItem

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedCategory = MutableStateFlow("Tous")
    val selectedCategory: StateFlow<String> = _selectedCategory

    // Favoris (liste d'ID)
    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites

    // Charger les favoris depuis SharedPreferences au lancement
    init {
        val sharedPrefs = context.getSharedPreferences("boutique_mode_prefs", Context.MODE_PRIVATE)
        val favSet = sharedPrefs.getStringSet("favorites", emptySet()) ?: emptySet()
        _favorites.value = favSet
        
        // Écouter les changements d'état de connexion pour adapter l'écran actif
        viewModelScope.launch {
            authService.authState.collect { state ->
                when (state) {
                    is AuthState.Authenticated -> {
                        if (state.role == UserRole.ADMIN) {
                            _currentScreen.value = "admin_catalog"
                        } else {
                            _currentScreen.value = "client_catalog"
                        }
                        refreshData()
                    }
                    is AuthState.Unauthenticated, is AuthState.Error -> {
                        _currentScreen.value = "login"
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Filtre dynamique des vêtements selon la recherche, la catégorie, et l'option "Favoris".
     */
    val filteredClothingItems: StateFlow<List<ClothingItem>> = combine(
        clothingItems,
        _searchQuery,
        _selectedCategory,
        _favorites
    ) { items, query, category, favs ->
        items.filter { item ->
            val matchesQuery = item.name.contains(query, ignoreCase = true) || 
                               item.description.contains(query, ignoreCase = true)
            val matchesCategory = category == "Tous" || 
                                  (category == "Favoris" && favs.contains(item.id)) || 
                                  item.category.lowercase() == category.lowercase()
            matchesQuery && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Actualise le catalogue et les commandes.
     */
    fun refreshData() {
        viewModelScope.launch {
            catalogService.fetchCatalog()
            orderService.fetchOrders()
        }
    }

    /**
     * Navigation.
     */
    fun navigateTo(screen: String) {
        _currentScreen.value = screen
    }

    fun selectClothingItem(item: ClothingItem) {
        _selectedClothingItem.value = item
        navigateTo("product_detail")
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    /**
     * Ajoute ou retire un vêtement des favoris.
     */
    fun toggleFavorite(itemId: String) {
        val currentFavs = _favorites.value.toMutableSet()
        if (currentFavs.contains(itemId)) {
            currentFavs.remove(itemId)
            Toast.makeText(context, "Retiré des favoris", Toast.LENGTH_SHORT).show()
        } else {
            currentFavs.add(itemId)
            Toast.makeText(context, "Ajouté aux favoris ❤️", Toast.LENGTH_SHORT).show()
        }
        _favorites.value = currentFavs

        // Sauvegarde locale
        val sharedPrefs = context.getSharedPreferences("boutique_mode_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putStringSet("favorites", currentFavs).apply()
    }

    /**
     * Lance le processus d'achat via WhatsApp :
     * 1. Enregistre une commande locale/Firestore pour l'administrateur.
     * 2. Ouvre WhatsApp avec un message pré-rempli contenant le nom du vêtement, le prix et la taille choisie.
     */
    fun orderViaWhatsApp(item: ClothingItem, selectedSize: String, clientEmail: String) {
        viewModelScope.launch {
            // Créer l'objet de commande pour le tableau de bord admin
            val order = OrderItem(
                clientEmail = if (clientEmail.isEmpty()) "Visiteur Anonyme" else clientEmail,
                clothingId = item.id,
                clothingName = item.name,
                selectedSize = selectedSize,
                status = "Nouveau"
            )
            val success = orderService.placeOrder(order)
            if (success) {
                Log.d(tag, "Commande enregistrée avec succès dans la base de données.")
            }

            // Préparer le message WhatsApp personnalisé
            val message = "Bonjour ! Je souhaite commander cet article depuis votre application Boutique Mode :\n\n" +
                          "🔹 *Article* : ${item.name}\n" +
                          "🔹 *Prix* : ${String.format("%.2f", item.price)} €\n" +
                          "🔹 *Taille sélectionnée* : $selectedSize\n\n" +
                          "Merci de me confirmer la disponibilité !"

            try {
                // Numéro de téléphone de l'ami créateur (remplaçable ou à configurer, par défaut un placeholder international)
                val phoneNumber = "33600000000" // Numéro de test modifiable par l'admin
                val url = "https://api.whatsapp.com/send?phone=$phoneNumber&text=${URLEncoder.encode(message, "UTF-8")}"
                
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(url)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(tag, "Erreur lors du lancement de WhatsApp", e)
                Toast.makeText(context, "Erreur: WhatsApp n'est pas installé ou impossible de l'ouvrir.", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Crée, met à jour ou supprime un article du catalogue (pour l'admin).
     */
    fun saveClothingItem(item: ClothingItem, isEdit: Boolean, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = if (isEdit) {
                catalogService.updateClothingItem(item)
            } else {
                catalogService.addClothingItem(item)
            }
            if (success) {
                Toast.makeText(context, "Catalogue mis à jour avec succès !", Toast.LENGTH_SHORT).show()
                refreshData()
            } else {
                Toast.makeText(context, "Une erreur est survenue lors de l'enregistrement.", Toast.LENGTH_SHORT).show()
            }
            onComplete(success)
        }
    }

    fun deleteClothingItem(itemId: String) {
        viewModelScope.launch {
            val success = catalogService.deleteClothingItem(itemId)
            if (success) {
                Toast.makeText(context, "Article supprimé du catalogue.", Toast.LENGTH_SHORT).show()
                refreshData()
            } else {
                Toast.makeText(context, "Erreur de suppression.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Met à jour le statut d'une commande (ex : Marqué comme traité).
     */
    fun updateOrderStatus(orderId: String, newStatus: String) {
        viewModelScope.launch {
            val success = orderService.updateOrderStatus(orderId, newStatus)
            if (success) {
                Toast.makeText(context, "Statut de commande mis à jour.", Toast.LENGTH_SHORT).show()
                refreshData()
            }
        }
    }
}
