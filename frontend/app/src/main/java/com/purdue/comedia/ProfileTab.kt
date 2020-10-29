package com.purdue.comedia

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.core.graphics.drawable.RoundedBitmapDrawable
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.android.synthetic.main.post_row.view.*
import kotlinx.android.synthetic.main.profile_tab.*
import java.io.BufferedInputStream
import java.net.URL


/**
 * A Fragment representing the Profile Tab
 */
class ProfileTab : Fragment() {
    // 3. Declare Parameters here
    //private lateinit var adapter: MainAdapter
    private var auth: FirebaseAuth = FirebaseAuth.getInstance()
    private var savedProfileUrl = ""
    private var promptedForProfile = false

    private var theLoginBtn: Button? = null

    override fun onStart() {
        super.onStart()
        updateProfile()
    }

    // Gets run everytime the screen is presented
    override fun onResume() {
        super.onResume()

        // Change Sign In Button Text
        if (theLoginBtn != null && auth.currentUser != null) {
            theLoginBtn!!.text = "Sign Out"

        } else if (theLoginBtn != null) {
            theLoginBtn!!.text = "Sign In"
        }

        updateProfile() // Reload data from firebase each time profile page loads
        updateTableData() // Reload feed of posts by current user

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val root = inflater.inflate(R.layout.profile_tab, container, false)

        /** Setup the elements of the view here **/

        // View saved posts
        val btnViewSavedPosts: Button = root.findViewById(R.id.btnViewSavedPosts)
        btnViewSavedPosts.setOnClickListener {
            val intent = Intent(view!!.context, CustomFeed::class.java)
            intent.putExtra(MainAdapter.NAV_TITLE, "Saved Posts")
            startActivity(intent)
        }

        // Login Button Functionality
        val loginBtn: Button = root.findViewById(R.id.btnToLoginPage)
        theLoginBtn = loginBtn
        if (auth.currentUser != null) {
            loginBtn.text = "Sign out"
        } else {
            loginBtn.text = "Sign In"
        }

        loginBtn.setOnClickListener {
            promptedForProfile = false
            if (auth.currentUser != null) {
                // Sign Out
                auth.signOut()
                loginBtn.text = "Sign In"
            }
            startActivity(Intent(view!!.context, SignUp::class.java))
        }

        // Edit Profile Button Functionality
        val btnEditProfile: Button = root.findViewById(R.id.btnEditProfile)
        btnEditProfile.setOnClickListener {
            if (auth.currentUser != null) {
                textInputAlert() // Present alert to input the new text
            } else {
                // User not signed in, present alert
                Snackbar.make(
                    root.findViewById(R.id.profileScreen),
                    "You must be signed in to edit your profile", Snackbar.LENGTH_LONG
                )
                    .setAction("Login") {
                        startActivity(Intent(view!!.context, SignUp::class.java))
                    }.show()
            }
        }

        // Browse Genre Button Functionality
        val btnBrowseGenre: Button = root.findViewById(R.id.btnBrowseGenre)
        btnBrowseGenre.setOnClickListener {
            handleBrowse(browsingGenre = true)
        }

        // Browse User Button Functionality
        val btnBrowseUser: Button = root.findViewById(R.id.btnBrowseUser)
        btnBrowseUser.setOnClickListener {
            handleBrowse(browsingGenre = false)
        }

        // Genre Button functionality
        val btnViewSavedGenres: Button = root.findViewById(R.id.btnViewSavedGenres)
        btnViewSavedGenres.setOnClickListener {
            val intent = Intent(view!!.context, GenresAndUserList::class.java)
            intent.putExtra(MainAdapter.IS_GENRE, true)
            startActivity(intent)
        }

        // Following Button functionality
        val btnViewFollowing: Button = root.findViewById(R.id.btnViewFollowing)
        btnViewFollowing.setOnClickListener {
            val intent = Intent(view!!.context, GenresAndUserList::class.java)
            intent.putExtra(MainAdapter.IS_GENRE, false)
            startActivity(intent)
        }

        // Delete Button Functionality
        val btnDeleteAccount: Button = root.findViewById(R.id.btnDeleteAccount)
        btnDeleteAccount.setOnClickListener {
            val alert = AlertDialog.Builder(context)
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to delete your account and all its data?")
                .setPositiveButton("Confirm") { dialog, _ ->
                    performAccountDeletion()
                    dialog.cancel()
                }.setNegativeButton("Cancel") { dialog, _ ->
                    dialog.cancel()
                }.create()
            alert.show()
        }

        val btnViewSelfProfile: Button = root.findViewById(R.id.btnViewSelfProfile)
        btnViewSelfProfile.setOnClickListener {
            goToProfile()
        }

        /* Setup Recycler View
        val recyclerView: RecyclerView = root.findViewById(R.id.myRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context) // Positions to absolute position
        adapter = MainAdapter()
        recyclerView.adapter = adapter // Setup table logic
        updateTableData()*/

        return root
    }

    private fun goToProfile() {
        val intent = Intent(context, ProfileView::class.java)
        intent.putExtra(MainAdapter.USERNAME, profileUsername.text.toString())
        startActivity(intent)
    }

    private fun performAccountDeletion() {
        // Todo: Delete the current user's account and log out
        toast("Todo: Delete Account")
    }

    private fun updateTableData() {
        /*if (!this::adapter.isInitialized) return
        FirestoreUtility.queryProfileFeed(FirebaseAuth.getInstance().uid!!).addOnSuccessListener {
            adapter.updateTable(FirestoreUtility.convertQueryToPosts(it))
        }*/
    }

    // Called when the profile page loads or when the profile has been edited
    private fun updateProfile() {
        loadUserProfile(auth.uid)
    }

    private fun loadUserProfile(uid: String?) {
        if (uid == null) return
        FirestoreUtility.queryForUserByUID(uid, ::loadProfileTabView) { e -> println(e) }
    }

    private fun loadProfileTabView(user: UserModel) {
        val profile = user.profile!!
        FirestoreUtility.resolveReference(profile, ::loadProfileFields)
        profileUsername.text = user.username
    }

    private fun loadProfileFields(snapshot: DocumentSnapshot) {
        val url = (snapshot.get("profileImage") as String?) ?: ""
        savedProfileUrl = url
        RetrieveImageTask(::setProfileImage).execute(url)
        val newBio = snapshot.get("biography") as String? ?: ""
        bioTextProfilePage.text = newBio
        if (url == "https://paradisevalleychristian.org/wp-content/uploads/2017/01/Blank-Profile.png" && newBio == "No bio yet!" && !promptedForProfile) {
            textInputAlert()
            promptedForProfile = true
        }
    }

    private fun handleBrowse(browsingGenre: Boolean) {
        var hint = "Genre: Eg. Pun"
        var title = "Follow Genre"
        var message = "Enter the genre you wish to follow"

        if (!browsingGenre) {
            hint = "Username"
            title = "Go to User"
            message = "Enter the username whose profile you wish to view"
        }

        val inputText = EditText(context)
        inputText.hint = hint

        val textInputLayout = TextInputLayout(context)
        textInputLayout.setPadding(
            resources.getDimensionPixelOffset(R.dimen.dp_19), 0,
            resources.getDimensionPixelOffset(R.dimen.dp_19), 0
        )

        textInputLayout.addView(inputText)

        val alert = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(textInputLayout)
            .setMessage(message)
            .setPositiveButton("Confirm") { dialog, _ ->
                // Handle new profile information
                val textVal = inputText.text.toString()
                if (textVal.isEmpty()) dialog.cancel()
                else performBrowse(isGenre = browsingGenre, textVal.trim())
                dialog.cancel()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }.create()

        alert.show()
    }

    private fun performBrowse(isGenre: Boolean, inputText: String) {
        if (isGenre) followGenre(inputText)
        else viewProfile(inputText)
    }

    private fun followGenre(genre: String) {
        FirestoreUtility.followGenre(genre)
    }

    private fun viewProfile(username: String) {
        val intent = Intent(context, ProfileView::class.java)
        intent.putExtra(MainAdapter.USERNAME, username)
        startActivity(intent)
    }

    class RetrieveImageTask(
        imageCallback: (Bitmap?, ImageView?) -> Unit,
        imageToChange: ImageView? = null
    ) :
        AsyncTask<String, Void, Bitmap?>() {
        val setImageView: (Bitmap?, ImageView?) -> Unit = imageCallback
        private val img = imageToChange

        override fun doInBackground(vararg urlArgs: String?): Bitmap? {
            return downloadImage(urlArgs[0] ?: "")
        }

        private fun downloadImage(url: String): Bitmap? {
            return try {
                val avatarUrl = URL(url)
                val connection = avatarUrl.openConnection()
                connection.connect()
                val stream = BufferedInputStream(connection.getInputStream())
                BitmapFactory.decodeStream(stream)
            } catch (e: Exception) {
                println(e)
                null
            }
        }

        override fun onPostExecute(result: Bitmap?) {
            super.onPostExecute(result)
            this.setImageView(result, img)
        }
    }

    private fun setProfileImage(image: Bitmap?, toSetProfileImg: ImageView?) {
        val drawable: RoundedBitmapDrawable = RoundedBitmapDrawableFactory.create(resources, image)
        drawable.isCircular = true
        profileImage.setImageBitmap(image)
        profileImage.setImageDrawable(drawable)
    }

    private fun textInputAlert() {
        val bioText = EditText(context)
        bioText.hint = "Bio"
        val profileUrl = EditText(context)
        profileUrl.hint = "Profile"

        val subTextInputLayout = TextInputLayout(context)
        subTextInputLayout.addView(bioText)

        val textInputLayout = TextInputLayout(context)
        textInputLayout.setPadding(
            resources.getDimensionPixelOffset(R.dimen.dp_19), 0,
            resources.getDimensionPixelOffset(R.dimen.dp_19), 0
        )

        textInputLayout.addView(profileUrl)
        textInputLayout.addView(subTextInputLayout)

        val alert = AlertDialog.Builder(context)
            .setTitle("Edit Profile")
            .setView(textInputLayout)
            .setMessage("Profile is empty. Please Enter bio and Image URL for profile")
            .setPositiveButton("Confirm") { dialog, _ ->
                // Handle new profile information
                if (!URLUtil.isValidUrl(profileUrl.text.toString())) {
                    profileUrl.setText(savedProfileUrl)
                }
                if (bioText.text.isEmpty()) bioText.setText(bioTextProfilePage.text)
                if (profileUrl.text.isEmpty()) profileUrl.setText(savedProfileUrl)
                editProfileOntoFirebase(profileUrl.text.toString(), bioText.text.toString())
                updateProfile()
                toast("Profile Updated")
                dialog.cancel()

            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }.create()

        alert.show()

    }

    // Called once a user edits the profile. New strings are passed to this function.
    private fun editProfileOntoFirebase(profileImageUrl: String = "", bioText: String) {
        if (auth.uid == null) return
        if (profileImageUrl.isEmpty() && bioText.isEmpty()) return
        val profileModel = PartialProfileModel()
        profileModel.profileImage = profileImageUrl
        profileModel.biography = bioText
        FirestoreUtility.updateUserProfile(auth.uid as String, profileModel)
    }

    private fun toast(str: String) {
        Toast.makeText(context, str, Toast.LENGTH_LONG).show()
    }

}
