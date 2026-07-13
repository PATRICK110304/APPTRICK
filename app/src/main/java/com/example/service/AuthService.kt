package com.example.service

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

/**
 * Rôles utilisateurs pour la Boutique Mode.
 */
enum class UserRole {
    CLIENT,
    ADMIN
}

/**
 * États d'authentification possibles.
 */
sealed class AuthState {
    object Initial : AuthState()
    object Loading : AuthState()
    data class Authenticated(val userId: String, val email: String, val role: UserRole) : AuthState()
    object Unauthenticated : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * Service d'authentification gérant la connexion Firebase Auth et la redirection
 * basée sur les rôles stockés dans Cloud Firestore.
 * Intègre un mode de simulation robuste au cas où Firebase n'est pas encore configuré (fichier google-services.json manquant).
 */
class AuthService {

    private val tag = "AuthService"

    // Flux d'état de l'authentification
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState

    // Instances Firebase avec gestion sécurisée des erreurs d'initialisation
    private var firebaseAuth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null

    // Mode simulation activé par défaut si Firebase n'est pas configuré
    var isSimulationMode = false
        private set

    init {
        try {
            firebaseAuth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance()
            Log.d(tag, "Firebase initialisé avec succès.")
            checkCurrentUser()
        } catch (e: Exception) {
            Log.w(tag, "Firebase n'est pas configuré (google-services.json manquant). Mode simulation activé.", e)
            isSimulationMode = true
            _authState.value = AuthState.Unauthenticated
        }
    }

    /**
     * Vérifie l'état de l'utilisateur actuellement connecté.
     */
    fun checkCurrentUser() {
        if (isSimulationMode) {
            return
        }

        val currentUser = firebaseAuth?.currentUser
        if (currentUser == null) {
            _authState.value = AuthState.Unauthenticated
            return
        }

        _authState.value = AuthState.Loading
        // Récupérer le rôle de l'utilisateur dans Firestore
        val userId = currentUser.uid
        val email = currentUser.email ?: ""

        firestore?.collection("users")?.document(userId)?.get()
            ?.addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val roleStr = document.getString("role") ?: "CLIENT"
                    val role = if (roleStr.uppercase() == "ADMIN") UserRole.ADMIN else UserRole.CLIENT
                    _authState.value = AuthState.Authenticated(userId, email, role)
                } else {
                    // Par défaut, si aucun rôle n'est spécifié, l'utilisateur est un Client
                    _authState.value = AuthState.Authenticated(userId, email, UserRole.CLIENT)
                }
            }
            ?.addOnFailureListener { e ->
                Log.e(tag, "Erreur de récupération du rôle Firestore", e)
                // Fallback client en cas de problème de connexion Firestore
                _authState.value = AuthState.Authenticated(userId, email, UserRole.CLIENT)
            }
    }

    /**
     * Connexion avec Email et Mot de passe.
     */
    suspend fun signIn(email: String, password: String) {
        _authState.value = AuthState.Loading

        if (isSimulationMode) {
            kotlinx.coroutines.delay(1000) // Simulation de latence réseau
            if (email.lowercase().contains("admin")) {
                _authState.value = AuthState.Authenticated("sim_admin_123", email, UserRole.ADMIN)
            } else {
                _authState.value = AuthState.Authenticated("sim_client_123", email, UserRole.CLIENT)
            }
            return
        }

        try {
            val result = firebaseAuth?.signInWithEmailAndPassword(email, password)?.await()
            val user = result?.user
            if (user != null) {
                // Récupération du rôle
                fetchRoleAndEmitState(user.uid, user.email ?: "")
            } else {
                _authState.value = AuthState.Error("Erreur d'authentification.")
            }
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.localizedMessage ?: "Une erreur s'est produite.")
        }
    }

    /**
     * Inscription d'un nouvel utilisateur.
     */
    suspend fun signUp(email: String, password: String, role: UserRole = UserRole.CLIENT) {
        _authState.value = AuthState.Loading

        if (isSimulationMode) {
            kotlinx.coroutines.delay(1000)
            _authState.value = AuthState.Authenticated("sim_new_user", email, role)
            return
        }

        try {
            val result = firebaseAuth?.createUserWithEmailAndPassword(email, password)?.await()
            val user = result?.user
            if (user != null) {
                // Enregistrer le rôle dans Firestore
                val userMap = mapOf(
                    "email" to email,
                    "role" to role.name
                )
                firestore?.collection("users")?.document(user.uid)?.set(userMap)?.await()
                _authState.value = AuthState.Authenticated(user.uid, email, role)
            } else {
                _authState.value = AuthState.Error("Création de compte échouée.")
            }
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.localizedMessage ?: "Une erreur est survenue lors de l'inscription.")
        }
    }

    /**
     * Déconnexion de l'utilisateur.
     */
    fun signOut() {
        if (!isSimulationMode) {
            firebaseAuth?.signOut()
        }
        _authState.value = AuthState.Unauthenticated
    }

    /**
     * Permet de forcer l'état de simulation pour tester facilement les deux interfaces (Client / Admin)
     */
    fun forceSimulationRole(role: UserRole) {
        _authState.value = AuthState.Authenticated(
            userId = if (role == UserRole.ADMIN) "sim_admin_id" else "sim_client_id",
            email = if (role == UserRole.ADMIN) "admin@boutiquemode.com" else "client@boutiquemode.com",
            role = role
        )
    }

    private suspend fun fetchRoleAndEmitState(userId: String, email: String) {
        try {
            val document = firestore?.collection("users")?.document(userId)?.get()?.await()
            if (document != null && document.exists()) {
                val roleStr = document.getString("role") ?: "CLIENT"
                val role = if (roleStr.uppercase() == "ADMIN") UserRole.ADMIN else UserRole.CLIENT
                _authState.value = AuthState.Authenticated(userId, email, role)
            } else {
                _authState.value = AuthState.Authenticated(userId, email, UserRole.CLIENT)
            }
        } catch (e: Exception) {
            // Fallback client
            _authState.value = AuthState.Authenticated(userId, email, UserRole.CLIENT)
        }
    }
}
