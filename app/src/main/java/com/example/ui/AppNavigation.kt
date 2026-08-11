package com.example.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.AddContactOptionsScreen
import com.example.ui.screens.AddContactScreen
import com.example.ui.screens.ContactsScreen
import com.example.viewmodel.ContactsViewModel

@Composable
fun AppNavigation(viewModel: ContactsViewModel) {
    val navController = rememberNavController()
    
    NavHost(navController = navController, startDestination = "contacts") {
        composable("contacts") {
            ContactsScreen(
                viewModel = viewModel,
                onNavigateToAdd = { navController.navigate("add_contact_options") },
                onNavigateToEdit = { contact ->
                    val encodedName = android.net.Uri.encode(contact.name)
                    val encodedPhone = android.net.Uri.encode(contact.phoneNumber)
                    val encodedImg = android.net.Uri.encode(contact.imageUri ?: "")
                    navController.navigate("add_contact?id=${contact.id}&name=$encodedName&phone=$encodedPhone&imageUri=$encodedImg")
                }
            )
        }
        composable("add_contact_options") {
            AddContactOptionsScreen(
                onNavigateToNewContact = { 
                    navController.navigate("add_contact") {
                        popUpTo("add_contact_options") { inclusive = true }
                    } 
                },
                onNavigateToContactWithDetails = { name, phone -> 
                    val encodedName = android.net.Uri.encode(name)
                    val encodedPhone = android.net.Uri.encode(phone)
                    navController.navigate("add_contact?name=$encodedName&phone=$encodedPhone") {
                        popUpTo("add_contact_options") { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "add_contact?id={id}&name={name}&phone={phone}&imageUri={imageUri}",
            arguments = listOf(
                navArgument("id") { type = NavType.IntType; defaultValue = 0 },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
                navArgument("phone") { type = NavType.StringType; defaultValue = "" },
                navArgument("imageUri") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("id") ?: 0
            val name = backStackEntry.arguments?.getString("name") ?: ""
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            val imageUri = backStackEntry.arguments?.getString("imageUri")?.ifBlank { null }
            AddContactScreen(
                viewModel = viewModel,
                contactId = id,
                initialName = name,
                initialPhone = phone,
                initialImageUri = imageUri,
                onNavigateBack = { navController.popBackStack("contacts", inclusive = false) }
            )
        }
    }
}
