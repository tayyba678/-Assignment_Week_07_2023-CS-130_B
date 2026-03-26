package com.example.contact_app_recycler_view

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity(), ContactAdapter.OnContactActionListener {

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etSearch: EditText
    private lateinit var btnSave: Button
    private lateinit var btnLoadContacts: Button
    private lateinit var btnSort: Button
    private lateinit var recyclerViewContacts: RecyclerView
    private lateinit var imgProfile: ImageView // Activity-level profile picker

    private lateinit var contactAdapter: ContactAdapter
    private val contactList = mutableListOf<Contact>()
    private val fullContactList = mutableListOf<Contact>() // for search/filter
    private var isSortedAsc = true
    private var selectedProfileUri: Uri? = null

    // Permission for reading contacts
    private val requestContactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) loadContactsFromPhone()
            else Toast.makeText(this, "Contacts permission denied", Toast.LENGTH_SHORT).show()
        }

    // Pick image from gallery for new contact
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                selectedProfileUri = uri
                imgProfile.setImageURI(uri)
            }
        }

    // --- NEW: Edit dialog image picker ---
    private var currentEditImageView: ImageView? = null
    private var selectedEditProfileUri: Uri? = null
    private val pickEditImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                selectedEditProfileUri = uri
                currentEditImageView?.setImageURI(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind activity-level views
        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        etSearch = findViewById(R.id.etSearch)
        btnSave = findViewById(R.id.btnSave)
        btnLoadContacts = findViewById(R.id.btnLoadContacts)
        btnSort = findViewById(R.id.btnSort)
        recyclerViewContacts = findViewById(R.id.recyclerViewContacts)
        imgProfile = findViewById(R.id.imgProfile) // For adding new contact only

        // Optional: adjust padding for system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Setup RecyclerView
        contactAdapter = ContactAdapter(contactList, this)
        recyclerViewContacts.layoutManager = LinearLayoutManager(this)
        recyclerViewContacts.adapter = contactAdapter

        // Keep a copy for search/filter
        fullContactList.addAll(contactList)

        // Activity-level profile selection
        imgProfile.setOnClickListener { pickImageLauncher.launch("image/*") }

        btnSave.setOnClickListener { saveContact() }
        btnLoadContacts.setOnClickListener { checkPermissionAndLoadContacts() }

        // Search
        etSearch.addTextChangedListener { text ->
            val filtered = fullContactList.filter {
                it.name.contains(text.toString(), ignoreCase = true)
            }
            contactList.clear()
            contactList.addAll(filtered)
            contactAdapter.notifyDataSetChanged()
        }

        // Sort
        btnSort.setOnClickListener {
            if (isSortedAsc) {
                contactList.sortBy { it.name.lowercase() }
                btnSort.text = "Sort Z-A"
            } else {
                contactList.sortByDescending { it.name.lowercase() }
                btnSort.text = "Sort A-Z"
            }
            isSortedAsc = !isSortedAsc
            contactAdapter.notifyDataSetChanged()
        }
    }

    private fun saveContact() {
        val name = etName.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        if (!validateInputs(name, phone, etName, etPhone)) return

        val newContact = Contact(name, phone, selectedProfileUri)
        contactList.add(newContact)
        fullContactList.add(newContact)
        contactAdapter.notifyItemInserted(contactList.size - 1)
        recyclerViewContacts.scrollToPosition(contactList.size - 1)

        Toast.makeText(this, "Contact saved successfully", Toast.LENGTH_SHORT).show()

        // Reset inputs
        etName.text.clear()
        etPhone.text.clear()
        selectedProfileUri = null
        imgProfile.setImageResource(R.drawable.ic_person_placeholder)
        etName.requestFocus()
    }

    private fun validateInputs(name: String, phone: String, nameInput: EditText, phoneInput: EditText): Boolean {
        var isValid = true
        if (name.isEmpty()) { nameInput.error = "Name is required"; isValid = false }
        if (phone.isEmpty()) { phoneInput.error = "Phone is required"; isValid = false }
        else if (phone.length < 10 || !phone.all { it.isDigit() || it == '+' }) {
            phoneInput.error = "Enter valid phone number"; isValid = false
        }
        return isValid
    }

    override fun onItemClick(position: Int) {
        val contact = contactList[position]
        Toast.makeText(this, "Contact: ${contact.name}\nPhone: ${contact.phone}", Toast.LENGTH_SHORT).show()
    }

    override fun onEditClick(position: Int) {
        showEditDialog(position)
    }

    override fun onDeleteClick(position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Delete Contact")
            .setMessage("Are you sure you want to delete this contact?")
            .setPositiveButton("Yes") { _, _ ->
                contactList.removeAt(position)
                fullContactList.removeAt(position)
                contactAdapter.notifyItemRemoved(position)
                contactAdapter.notifyItemRangeChanged(position, contactList.size)
                Toast.makeText(this, "Contact deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun checkPermissionAndLoadContacts() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED ->
                loadContactsFromPhone()
            shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) ->
                AlertDialog.Builder(this)
                    .setTitle("Permission Required")
                    .setMessage("This app needs permission to read your contacts to display them.")
                    .setPositiveButton("Grant") { _, _ -> requestContactsPermission.launch(Manifest.permission.READ_CONTACTS) }
                    .setNegativeButton("Deny", null)
                    .show()
            else -> requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun loadContactsFromPhone() {
        val loadedContacts = mutableListOf<Contact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )
        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameIndex) ?: ""
                val phone = it.getString(phoneIndex) ?: ""
                if (name.isNotBlank() && phone.isNotBlank()) {
                    loadedContacts.add(Contact(name, phone, null))
                }
            }
        }

        if (loadedContacts.isEmpty()) {
            Toast.makeText(this, "No contacts found on your phone", Toast.LENGTH_SHORT).show()
            return
        }

        contactList.clear()
        contactList.addAll(loadedContacts)
        fullContactList.clear()
        fullContactList.addAll(loadedContacts)
        contactAdapter.notifyDataSetChanged()
        Toast.makeText(this, "${loadedContacts.size} contacts loaded", Toast.LENGTH_SHORT).show()
    }

    private fun showEditDialog(position: Int) {
        val dialogView = layoutInflater.inflate(R.layout.activity_dialog_edit_item, null)
        val etEditName = dialogView.findViewById<EditText>(R.id.etEditName)
        val etEditPhone = dialogView.findViewById<EditText>(R.id.etEditPhone)
        val imgEditProfile = dialogView.findViewById<ImageView>(R.id.imgEditProfile)

        val contact = contactList[position]
        etEditName.setText(contact.name)
        etEditPhone.setText(contact.phone)
        if (contact.profileUri != null) imgEditProfile.setImageURI(contact.profileUri)
        else imgEditProfile.setImageResource(R.drawable.ic_person_placeholder)

        // ✅ Fixed: use Activity-level launcher for edit
        imgEditProfile.setOnClickListener {
            currentEditImageView = imgEditProfile
            selectedEditProfileUri = contact.profileUri
            pickEditImageLauncher.launch("image/*")
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit Contact")
            .setView(dialogView)
            .setPositiveButton("Update", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val updatedName = etEditName.text.toString().trim()
            val updatedPhone = etEditPhone.text.toString().trim()
            if (validateInputs(updatedName, updatedPhone, etEditName, etEditPhone)) {
                contact.name = updatedName
                contact.phone = updatedPhone
                if (selectedEditProfileUri != null) contact.profileUri = selectedEditProfileUri
                contactAdapter.notifyItemChanged(position)
                Toast.makeText(this, "Contact updated", Toast.LENGTH_SHORT).show()
                dialog.dismiss()

                // Reset temporary vars
                selectedEditProfileUri = null
                currentEditImageView = null
            }
        }
    }
}