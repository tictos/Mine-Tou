package com.example.data

import kotlinx.coroutines.flow.Flow

class ContactRepository(private val contactDao: ContactDao) {
    val allContacts: Flow<List<Contact>> = contactDao.getAllContacts()

    suspend fun insert(contact: Contact) = contactDao.insertContact(contact)

    suspend fun deleteById(id: Int) = contactDao.deleteContactById(id)
}
