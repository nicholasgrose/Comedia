package com.purdue.comedia

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class AuthUtility {
    companion object {
        private val auth = FirebaseAuth.getInstance()
        private val firestore = FirebaseFirestore.getInstance()

        fun createAccount(email: String, password: String): Task<AuthResult> {
            return auth.createUserWithEmailAndPassword(email, password)
        }

        fun signIn(email: String, password: String): Task<AuthResult> {
            return auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { FirestoreUtility.addListenerForCurrentUser() }
        }

        fun signOut() {
            auth.signOut()
            FirestoreUtility.clearCurrentUserListener()
        }

        fun deleteAccount(): Task<Unit> {
            return FirestoreUtility.queryForUserByUID(auth.uid!!)
                .continueWithTask { removeInteractions(it.result!!) }
                .continueWithTask { deletePosts(it.result!!) }
                .continueWithTask { removeFromFollowLists(it.result!!) }
                .continueWithTask { deleteUser(it.result!!) }
                .continueWith { auth.signOut() }
        }

        private fun removeInteractions(user: UserModel): Task<UserModel> {
            return Tasks.whenAll(
                deleteComments(user.comments),
                revertVoteCounts(user)
            ).continueWith { user }
        }

        private fun deleteComments(comments: List<DocumentReference>): Task<Void> {
            val commentTasks = ArrayList<Task<Void>>()
            for (comment in comments) {
                commentTasks.add(FirestoreUtility.resolveCommentReference(comment)
                    .continueWithTask {
                        Tasks.whenAll(
                            it.result!!.parent.update(
                                "comments",
                                FieldValue.arrayRemove(comment)
                            ),
                            comment.delete()
                        )
                    })
            }
            return Tasks.whenAll(commentTasks)
        }

        private fun revertVoteCounts(user: UserModel): Task<Void> {
            val reversionTasks = ArrayList<Task<Void>>()
            val userRef = FirestoreUtility.userRefByUID(auth.uid!!)
            for (post in user.upvotedPosts) {
                reversionTasks.add(
                    Tasks.whenAll(
                        post.update("upvoteCount", FieldValue.increment(-1)),
                        post.update("upvoteList", FieldValue.arrayRemove(userRef))
                    )
                )
            }
            for (post in user.downvotedPosts) {
                reversionTasks.add(
                    Tasks.whenAll(
                        post.update("downvoteCount", FieldValue.increment(-1)),
                        post.update("downvoteList", FieldValue.arrayRemove(userRef))
                    )
                )
            }
            return Tasks.whenAll(reversionTasks)
        }

        private fun deletePosts(user: UserModel): Task<UserModel> {
            val deleteTasks = ArrayList<Task<Void>>()
            for (post in user.createdPosts) {
                deleteTasks.add(
                    FirestoreUtility.resolvePostReference(post)
                        .continueWithTask {
                            deletePost(post, it.result!!)
                        }
                )
            }
            return Tasks.whenAll(deleteTasks).continueWith { user }
        }

        private fun deletePost(post: DocumentReference, postModel: PostModel): Task<Void> {
            val downvoteTasks = postModel.downvoteList
                .map { it.update("downvotedPosts", FieldValue.arrayRemove(post)) }
            val upvoteTasks = postModel.upvoteList
                .map { it.update("upvotedPosts", FieldValue.arrayRemove(post)) }
            val savedPostTasks = postModel.saveList
                .map { it.update("savedPosts", FieldValue.arrayRemove(post)) }

            return Tasks.whenAll(
                Tasks.whenAll(downvoteTasks),
                Tasks.whenAll(upvoteTasks),
                Tasks.whenAll(savedPostTasks),
                deleteComments(postModel.comments),
                post.delete()
            )
        }

        private fun removeFromFollowLists(user: UserModel): Task<UserModel> {
            val removalTasks = ArrayList<Task<Void>>()
            val userRef = FirestoreUtility.userRefByUID(auth.uid!!)

            for (follower in user.followers) {
                removalTasks.add(
                    follower.update("usersFollowing", FieldValue.arrayRemove(userRef))
                )
            }
            for (followed in user.usersFollowing) {
                removalTasks.add(
                    followed.update("followers", FieldValue.arrayRemove(userRef))
                )
            }

            return Tasks.whenAll(removalTasks).continueWith { user }
        }

        private fun deleteUser(userModel: UserModel): Task<Void> {
            val deleteUser = firestore.collection("users")
                .document(auth.uid!!)
                .delete()
            val deleteUserLookup = firestore.collection("userLookup")
                .document(userModel.username)
                .delete()
            return Tasks.whenAll(deleteUser, deleteUserLookup)
        }

        fun addNewUser(uid: String, username: String, email: String): Task<Void> {
            return Tasks.whenAll(
                createNewUserLookup(uid, username, email),
                createNewUser(username, email)
            )
        }

        private fun createNewUserLookup(uid: String, username: String, email: String): Task<Void> {
            val model = UserLookupModel()
            model.email = email
            model.uid = uid
            return firestore.collection("userLookup").document(username).set(model)
        }

        // Called after a new user has registered
        private fun createNewUser(username: String, email: String): Task<Void> {
            val userModel = UserModel()
            userModel.username = username
            userModel.email = email
            val user = firestore.collection("users").document(auth.uid!!)

            val profileModel = ProfileModel()
            profileModel.biography = "No bio yet!"
            profileModel.profileImage =
                "https://paradisevalleychristian.org/wp-content/uploads/2017/01/Blank-Profile.png"
            profileModel.user = user

            return firestore.collection("profiles").add(profileModel)
                .continueWithTask {
                    userModel.profile = it.result!!
                    return@continueWithTask user.set(userModel)
                }
        }
    }
}
