package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.R
import com.example.model.ClothingItem
import com.example.service.OrderItem
import com.example.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCatalogScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Catalogue, 1 = Commandes
    val clothingItems by viewModel.clothingItems.collectAsState()
    val orders by viewModel.orders.collectAsState()

    var showFormDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ClothingItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PANNEAU ADMINISTRATEUR",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                },
                actions = {
                    // Bouton pour aller voir le catalogue côté client
                    IconButton(onClick = { viewModel.navigateTo("client_catalog") }) {
                        Icon(Icons.Default.Storefront, contentDescription = "Mode Client")
                    }
                    IconButton(
                        onClick = { viewModel.authService.signOut() },
                        modifier = Modifier.testTag("admin_logout_button")
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = "Se déconnecter", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        editingItem = null
                        showFormDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_clothing_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Ajouter un vêtement")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            // Sélecteur d'onglets (TabRow)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Stock Catalogue", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Inventory, contentDescription = null) },
                    modifier = Modifier.testTag("tab_catalog")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Commandes & Contacts", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.ShoppingBag, contentDescription = null) },
                    modifier = Modifier.testTag("tab_orders")
                )
            }

            if (selectedTab == 0) {
                // Gestion du Catalogue
                if (clothingItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Inbox,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Le catalogue est vide.", style = MaterialTheme.typography.titleMedium)
                            Text("Cliquez sur le bouton + pour ajouter un vêtement.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(clothingItems) { item ->
                            AdminClothingRow(
                                item = item,
                                onEdit = {
                                    editingItem = item
                                    showFormDialog = true
                                },
                                onDelete = { viewModel.deleteClothingItem(item.id) }
                            )
                        }
                    }
                }
            } else {
                // Liste des Commandes reçues
                if (orders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.CheckCircleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Aucune commande en attente !", style = MaterialTheme.typography.titleMedium)
                            Text("Les demandes clients s'afficheront ici.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(orders) { order ->
                            AdminOrderCard(
                                order = order,
                                onStatusChange = { newStatus ->
                                    viewModel.updateOrderStatus(order.id, newStatus)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Boîte de dialogue du formulaire Ajouter / Éditer un vêtement
        if (showFormDialog) {
            ClothingFormDialog(
                item = editingItem,
                onDismiss = { showFormDialog = false },
                onSave = { name, price, desc, sizes, category, imageUrl ->
                    val finalItem = ClothingItem(
                        id = editingItem?.id ?: "",
                        name = name,
                        price = price,
                        description = desc,
                        sizes = sizes,
                        category = category,
                        imageUrl = imageUrl
                    )
                    viewModel.saveClothingItem(finalItem, isEdit = editingItem != null) { success ->
                        if (success) showFormDialog = false
                    }
                }
            )
        }
    }
}

@Composable
fun AdminClothingRow(
    item: ClothingItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.name,
                modifier = Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.ic_launcher_background),
                error = painterResource(id = R.drawable.ic_launcher_background)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.category} • ${String.format("%.2f", item.price)} €",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Tailles: ${item.sizes.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row {
                IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_button_${item.id}")) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Éditer", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_button_${item.id}")) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun AdminOrderCard(
    order: OrderItem,
    onStatusChange: (String) -> Unit
) {
    val date = Date(order.timestamp)
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dateStr = formatter.format(date)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (order.status == "Nouveau") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Client: ${order.clientEmail}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Badge de statut
                SuggestionChip(
                    onClick = { /* Pas d'action au clic du badge */ },
                    label = { Text(order.status) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        labelColor = if (order.status == "Nouveau") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Vêtement commandé : ${order.clothingName}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Taille choisie : ${order.selectedSize}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Date : $dateStr",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )

            if (order.status == "Nouveau") {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { onStatusChange("Traité") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("mark_processed_${order.id}")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Marquer comme traité", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClothingFormDialog(
    item: ClothingItem?,
    onDismiss: () -> Unit,
    onSave: (name: String, price: Double, desc: String, sizes: List<String>, category: String, imageUrl: String) -> Unit
) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var priceStr by remember { mutableStateOf(item?.price?.toString() ?: "") }
    var description by remember { mutableStateOf(item?.description ?: "") }
    var category by remember { mutableStateOf(item?.category ?: "T-shirts") }
    var imageUrl by remember { mutableStateOf(item?.imageUrl ?: "") }

    // Tailles multi-sélection
    val allSizes = listOf("S", "M", "L", "XL")
    val selectedSizes = remember { mutableStateListOf<String>().apply { addAll(item?.sizes ?: listOf("S", "M", "L", "XL")) } }

    val categories = listOf("T-shirts", "Pantalons", "Chaussures", "Robes", "Vestes", "Chemises")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (item == null) "Ajouter un vêtement" else "Modifier le vêtement",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom du vêtement") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("form_name")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("Prix (€)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("form_price")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Sélecteur de catégorie
                Text(
                    text = "Catégorie",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { cat ->
                        val isSelected = category == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sélecteur de tailles (Multi-select)
                Text(
                    text = "Tailles disponibles",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    allSizes.forEach { size ->
                        val isSelected = selectedSizes.contains(size)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) selectedSizes.remove(size) else selectedSizes.add(size)
                            },
                            label = { Text(size) },
                            modifier = Modifier.testTag("form_size_$size")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("form_desc")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    label = { Text("URL de la photo") },
                    placeholder = { Text("https://exemple.com/photo.jpg") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("form_image_url")
                )

                // Raccourci magique pour générer des images aléatoires magnifiques
                TextButton(
                    onClick = {
                        val randomFashionImages = listOf(
                            "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=500",
                            "https://images.unsplash.com/photo-1483985988355-763728e1935b?w=500",
                            "https://images.unsplash.com/photo-1490481651871-ab68de25d43d?w=500",
                            "https://images.unsplash.com/photo-1434389677669-e08b4cac3105?w=500",
                            "https://images.unsplash.com/photo-1539109136881-3be0616acf4b?w=500"
                        )
                        imageUrl = randomFashionImages.random()
                    },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Générer une photo de mode professionnelle")
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Annuler")
                    }

                    Button(
                        onClick = {
                            val price = priceStr.toDoubleOrNull() ?: 0.0
                            if (name.isNotEmpty() && price > 0.0) {
                                // Fallback photo s'il n'y en a pas
                                val finalImgUrl = imageUrl.ifEmpty { "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=500" }
                                onSave(name, price, description, selectedSizes.toList(), category, finalImgUrl)
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("form_save_button")
                    ) {
                        Text("Enregistrer")
                    }
                }
            }
        }
    }
}
