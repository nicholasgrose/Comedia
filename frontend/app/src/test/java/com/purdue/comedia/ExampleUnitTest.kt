package com.purdue.comedia

import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Test

import org.junit.Assert.*
import org.junit.BeforeClass
import java.util.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    private lateinit var firestore: FirebaseFirestore
    private lateinit var userModel: UserModel
    private lateinit var profileModel: ProfileModel

    @BeforeClass
    fun setupEmulators() {
        firestore = FirebaseFirestore.getInstance()
        firestore.useEmulator("localhost", 8080)

        addTestUser(firestore)
    }

    private fun addTestUser(firestoreInstance: FirebaseFirestore) {
        val user = firestoreInstance.collection("users").document("test")
        val profile = firestoreInstance.collection("profiles").document("test")

        userModel = UserModel()
        userModel.username = "test"
        userModel.email = "test@test.com"
        userModel.profile = profile

        profileModel = ProfileModel()
        profileModel.biography = "testing"
        profileModel.profileImage = "test.com/test.jpg"
        profileModel.user = user

        user.set(userModel)
        profile.set(profileModel)
    }

    @Test
    fun test_query_for_user_gives_correct_result_on_success() {
    }
}