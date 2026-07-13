package com.example.model

/**
 * Modèle de données représentant un vêtement dans le catalogue de la Boutique Mode.
 * Ce modèle est conçu pour être facilement sérialisé/désérialisé avec Firebase Firestore.
 */
data class ClothingItem(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val description: String = "",
    val sizes: List<String> = emptyList(),
    val category: String = "",
    val imageUrl: String = ""
) {
    /**
     * Convertit l'objet en Map pour l'envoi vers Firebase Firestore.
     */
    fun toMap(): Map<String, Any> {
        return mapOf(
            "name" to name,
            "price" to price,
            "description" to description,
            "sizes" to sizes,
            "category" to category,
            "imageUrl" to imageUrl
        )
    }

    companion object {
        /**
         * Crée une instance de ClothingItem à partir d'un dictionnaire Firestore.
         */
        fun fromMap(id: String, map: Map<String, Any>): ClothingItem {
            val name = map["name"] as? String ?: ""
            val price = (map["price"] as? Number)?.toDouble() ?: 0.0
            val description = map["description"] as? String ?: ""
            @Suppress("UNCHECKED_CAST")
            val sizes = map["sizes"] as? List<String> ?: emptyList()
            val category = map["category"] as? String ?: ""
            val imageUrl = map["imageUrl"] as? String ?: ""

            return ClothingItem(
                id = id,
                name = name,
                price = price,
                description = description,
                sizes = sizes,
                category = category,
                imageUrl = imageUrl
            )
        }
    }
}
