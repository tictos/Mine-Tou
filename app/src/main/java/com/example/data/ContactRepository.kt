package com.example.data

import kotlinx.coroutines.flow.Flow

class ContactRepository(
    private val contactDao: ContactDao,
    private val callLogDao: CallLogDao
) {
    val allContacts: Flow<List<Contact>> = contactDao.getAllContacts()
    val allCallLogs: Flow<List<CallLogEntity>> = callLogDao.getAllCallLogs()

    suspend fun insert(contact: Contact) = contactDao.insertContact(contact)
    suspend fun deleteById(id: Int) = contactDao.deleteContactById(id)

    suspend fun insertCallLog(callLog: CallLogEntity) = callLogDao.insertCallLog(callLog)
    suspend fun deleteCallLogById(id: String) = callLogDao.deleteCallLogById(id)
    suspend fun clearAllCallLogs() = callLogDao.clearAllCallLogs()
}
