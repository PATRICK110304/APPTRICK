package com.example.service

import android.util.Log
import com.example.model.ClothingItem
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

/**
 * Service gérant le catalogue de vêtements (CRUD).
 * Fonctionne en mode Firestore réel ou en mode simulation mémoire locale.
 */
class CatalogService(private val isSimulationMode: Boolean) {

    private val tag = "CatalogService"
    private var firestore: FirebaseFirestore? = null

    // Catalogue en mémoire locale pour le mode simulation
    private val _simulatedItems = MutableStateFlow<List<ClothingItem>>(emptyList())
    val clothingItems: StateFlow<List<ClothingItem>> = _simulatedItems

    init {
        if (!isSimulationMode) {
            try {
                firestore = FirebaseFirestore.getInstance()
                Log.d(tag, "CatalogService: Firestore initialisé.")
            } catch (e: Exception) {
                Log.e(tag, "CatalogService: Échec d'initialisation de Firestore. Forçage du mode simulation.", e)
            }
        }
        
        // Initialisation des données de simulation de base
        _simulatedItems.value = getInitialMockData()
    }

    /**
     * Récupère tous les articles du catalogue.
     */
    suspend fun fetchCatalog() {
        if (isSimulationMode || firestore == null) {
            // Le mode simulation utilise l'état réactif _simulatedItems déjà initialisé
            return
        }

        try {
            val snapshot = firestore!!.collection("clothes").get().await()
            val items = snapshot.documents.map { doc ->
                ClothingItem.fromMap(doc.id, doc.data ?: emptyMap())
            }
            _simulatedItems.value = items
        } catch (e: Exception) {
            Log.e(tag, "Erreur lors du chargement du catalogue Firestore. Fallback simulation.", e)
        }
    }

    /**
     * Ajoute un nouveau vêtement au catalogue.
     */
    suspend fun addClothingItem(item: ClothingItem): Boolean {
        return try {
            if (isSimulationMode || firestore == null) {
                val newId = "sim_item_${System.currentTimeMillis()}"
                val newItem = item.copy(id = newId)
                _simulatedItems.value = _simulatedItems.value + newItem
                true
            } else {
                val docRef = firestore!!.collection("clothes").add(item.toMap()).await()
                // Mettre à jour l'ID localement
                val newItem = item.copy(id = docRef.id)
                _simulatedItems.value = _simulatedItems.value + newItem
                true
            }
        } catch (e: Exception) {
            Log.e(tag, "Erreur lors de l'ajout du vêtement", e)
            false
        }
    }

    /**
     * Modifie un vêtement existant.
     */
    suspend fun updateClothingItem(item: ClothingItem): Boolean {
        return try {
            if (isSimulationMode || firestore == null) {
                _simulatedItems.value = _simulatedItems.value.map {
                    if (it.id == item.id) item else it
                }
                true
            } else {
                firestore!!.collection("clothes").document(item.id).set(item.toMap()).await()
                _simulatedItems.value = _simulatedItems.value.map {
                    if (it.id == item.id) item else it
                }
                true
            }
        } catch (e: Exception) {
            Log.e(tag, "Erreur lors de la modification du vêtement", e)
            false
        }
    }

    /**
     * Supprime un vêtement du catalogue.
     */
    suspend fun deleteClothingItem(itemId: String): Boolean {
        return try {
            if (isSimulationMode || firestore == null) {
                _simulatedItems.value = _simulatedItems.value.filter { it.id != itemId }
                true
            } else {
                firestore!!.collection("clothes").document(itemId).delete().await()
                _simulatedItems.value = _simulatedItems.value.filter { it.id != itemId }
                true
            }
        } catch (e: Exception) {
            Log.e(tag, "Erreur lors de la suppression du vêtement", e)
            false
        }
    }

    private fun getInitialMockData(): List<ClothingItem> {
        return listOf(
            ClothingItem(
                id = "mock_1",
                name = "T-Shirt Premium Coton",
                price = 35.00,
                description = "T-shirt ultra doux en coton 100% biologique. Coupe moderne et coutures renforcées pour un confort durable.",
                sizes = listOf("S", "M", "L", "XL"),
                category = "T-shirts",
                imageUrl = "https://images.unsplash.com/photo-1521572267360-ee0c2909d518?w=500"
            ),
            ClothingItem(
                id = "mock_2",
                name = "Jean Slim Indigo Classique",
                price = 85.00,
                description = "Jean en denim haut de gamme avec une touche d'élasthanne pour le confort. Teinte indigo naturelle.",
                sizes = listOf("M", "L", "XL"),
                category = "Pantalons",
                imageUrl = "https://images.unsplash.com/photo-1542272604-787c3835535d?w=500"
            ),
            ClothingItem(
                id = "mock_3",
                name = "Sneakers Minimalistes Cuir",
                price = 120.00,
                description = "Baskets élégantes en cuir blanc pleine fleur. Semelle cousue main et intérieur doublé pour un confort royal.",
                sizes = listOf("S", "M", "L"),
                category = "Chaussures",
                imageUrl = "https://images.unsplash.com/photo-1549298916-b41d501d3772?w=500"
            ),
            ClothingItem(
                id = "mock_4",
                name = "Robe d'Été Fleurie Légère",
                price = 95.00,
                description = "Robe fluide parfaite pour les journées ensoleillées. Tissu respirant et détails fleuris raffinés.",
                sizes = listOf("S", "M", "L"),
                category = "Robes",
                imageUrl = "https://images.unsplash.com/photo-1595777457583-95e059d581b8?w=500"
            ),
            ClothingItem(
                id = "mock_5",
                name = "Veste en Cuir Classic Biker",
                price = 249.00,
                description = "Veste biker en cuir de mouton véritable. Finitions en métal argenté et coupe cintrée intemporelle.",
                sizes = listOf("M", "L", "XL"),
                category = "Vestes",
                imageUrl = "https://images.unsplash.com/photo-1551028719-00167b16eac5?w=500"
            ),
            ClothingItem(
                id = "mock_6",
                name = "Chemise Lin Pure Blanche",
                price = 65.00,
                description = "Chemise légère en lin 100% naturel. Idéal pour un look estival élégant et décontracté.",
                sizes = listOf("S", "M", "L", "XL"),
                category = "Chemises",
                imageUrl = "https://images.unsplash.com/photo-1596755094514-f87e34085b2c?w=500"
            )
        )
    }
}
