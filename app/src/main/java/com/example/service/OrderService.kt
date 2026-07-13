package com.example.service

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

/**
 * Modèle pour une commande ou contact client.
 */
data class OrderItem(
    val id: String = "",
    val clientEmail: String = "",
    val clothingId: String = "",
    val clothingName: String = "",
    val selectedSize: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "Nouveau" // Nouveau, Traité, Annulé
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "clientEmail" to clientEmail,
            "clothingId" to clothingId,
            "clothingName" to clothingName,
            "selectedSize" to selectedSize,
            "timestamp" to timestamp,
            "status" to status
        )
    }

    companion object {
        fun fromMap(id: String, map: Map<String, Any>): OrderItem {
            return OrderItem(
                id = id,
                clientEmail = map["clientEmail"] as? String ?: "",
                clothingId = map["clothingId"] as? String ?: "",
                clothingName = map["clothingName"] as? String ?: "",
                selectedSize = map["selectedSize"] as? String ?: "",
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                status = map["status"] as? String ?: "Nouveau"
            )
        }
    }
}

/**
 * Service gérant l'historique des commandes et demandes de contact des clients.
 */
class OrderService(private val isSimulationMode: Boolean) {

    private val tag = "OrderService"
    private var firestore: FirebaseFirestore? = null

    // Stockage en mémoire locale
    private val _orders = MutableStateFlow<List<OrderItem>>(emptyList())
    val orders: StateFlow<List<OrderItem>> = _orders

    init {
        if (!isSimulationMode) {
            try {
                firestore = FirebaseFirestore.getInstance()
                Log.d(tag, "OrderService: Firestore initialisé.")
            } catch (e: Exception) {
                Log.e(tag, "OrderService: Échec d'initialisation Firestore.", e)
            }
        }

        // Quelques commandes d'exemple pour l'administrateur au lancement
        _orders.value = getInitialMockOrders()
    }

    /**
     * Récupère la liste de toutes les commandes pour l'interface administrateur.
     */
    suspend fun fetchOrders() {
        if (isSimulationMode || firestore == null) {
            return
        }

        try {
            val snapshot = firestore!!.collection("orders").get().await()
            val items = snapshot.documents.map { doc ->
                OrderItem.fromMap(doc.id, doc.data ?: emptyMap())
            }
            _orders.value = items.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e(tag, "Erreur de chargement des commandes. Fallback mémoire.", e)
        }
    }

    /**
     * Enregistre une nouvelle commande de vêtement.
     */
    suspend fun placeOrder(order: OrderItem): Boolean {
        return try {
            if (isSimulationMode || firestore == null) {
                val newId = "sim_order_${System.currentTimeMillis()}"
                val newOrderItem = order.copy(id = newId)
                _orders.value = listOf(newOrderItem) + _orders.value
                true
            } else {
                val docRef = firestore!!.collection("orders").add(order.toMap()).await()
                val newOrderItem = order.copy(id = docRef.id)
                _orders.value = listOf(newOrderItem) + _orders.value
                true
            }
        } catch (e: Exception) {
            Log.e(tag, "Erreur lors de la création de la commande", e)
            false
        }
    }

    /**
     * Met à jour le statut d'une commande (ex: Marquer comme Traité).
     */
    suspend fun updateOrderStatus(orderId: String, newStatus: String): Boolean {
        return try {
            if (isSimulationMode || firestore == null) {
                _orders.value = _orders.value.map {
                    if (it.id == orderId) it.copy(status = newStatus) else it
                }
                true
            } else {
                firestore!!.collection("orders").document(orderId).update("status", newStatus).await()
                _orders.value = _orders.value.map {
                    if (it.id == orderId) it.copy(status = newStatus) else it
                }
                true
            }
        } catch (e: Exception) {
            Log.e(tag, "Erreur de mise à jour du statut de commande", e)
            false
        }
    }

    private fun getInitialMockOrders(): List<OrderItem> {
        return listOf(
            OrderItem(
                id = "order_1",
                clientEmail = "sophie.dubois@gmail.com",
                clothingId = "mock_1",
                clothingName = "T-Shirt Premium Coton",
                selectedSize = "M",
                timestamp = System.currentTimeMillis() - 86400000, // 1 jour avant
                status = "Nouveau"
            ),
            OrderItem(
                id = "order_2",
                clientEmail = "jean.durand@live.fr",
                clothingId = "mock_3",
                clothingName = "Sneakers Minimalistes Cuir",
                selectedSize = "L",
                timestamp = System.currentTimeMillis() - 172800000, // 2 jours avant
                status = "Traité"
            )
        )
    }
}
