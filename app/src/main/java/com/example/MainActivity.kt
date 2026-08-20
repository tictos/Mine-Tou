package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.ContactRepository
import com.example.ui.AppNavigation
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ContactsViewModel
import com.example.viewmodel.ContactsViewModelFactory

class MainActivity : ComponentActivity() {
    private lateinit var database: AppDatabase
    private lateinit var viewModel: ContactsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        database = AppDatabase.getInstance(applicationContext)
        
        val repository = ContactRepository(database.contactDao(), database.callLogDao())
        val factory = ContactsViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[ContactsViewModel::class.java]

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.onAppResumed()
        }
    }
}
