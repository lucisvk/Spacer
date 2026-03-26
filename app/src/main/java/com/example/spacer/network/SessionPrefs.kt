package com.example.spacer.network

import android.content.Context

class SessionPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_LOGGED_IN, false)

    fun setLoggedIn(value: Boolean) {
        prefs.edit().putBoolean(KEY_LOGGED_IN, value).apply()
        if (!value) {
            // Reset profile-related cached values on logout.
            prefs.edit()
                .putString(KEY_PROFILE_NAME, "")
                .putString(KEY_ABOUT_ME, "")
                .putString(KEY_PROFILE_IMAGE_URI, null)
                .putString(KEY_LOCATION_LABEL, "")
                .putInt(KEY_HOSTED_COUNT, 0)
                .putInt(KEY_ATTENDED_COUNT, 0)
                .putInt(KEY_FRIENDS_COUNT, 0)
                .apply()
        }
    }

    fun saveProfileName(name: String) {
        prefs.edit().putString(KEY_PROFILE_NAME, name).apply()
    }

    fun getProfileName(): String = prefs.getString(KEY_PROFILE_NAME, "") ?: ""

    fun saveAboutMe(aboutMe: String) {
        prefs.edit().putString(KEY_ABOUT_ME, aboutMe).apply()
    }

    fun getAboutMe(): String = prefs.getString(KEY_ABOUT_ME, "") ?: ""

    fun saveProfileImageUri(uri: String?) {
        prefs.edit().putString(KEY_PROFILE_IMAGE_URI, uri).apply()
    }

    fun getProfileImageUri(): String? = prefs.getString(KEY_PROFILE_IMAGE_URI, null)

    fun saveLocationLabel(location: String) {
        prefs.edit().putString(KEY_LOCATION_LABEL, location).apply()
    }

    fun getLocationLabel(): String = prefs.getString(KEY_LOCATION_LABEL, "") ?: ""

    fun getHostedCount(): Int = prefs.getInt(KEY_HOSTED_COUNT, 0)

    fun getAttendedCount(): Int = prefs.getInt(KEY_ATTENDED_COUNT, 0)

    fun getFriendsCount(): Int = prefs.getInt(KEY_FRIENDS_COUNT, 0)

    fun incrementHostedCount() {
        prefs.edit().putInt(KEY_HOSTED_COUNT, getHostedCount() + 1).apply()
    }

    fun incrementAttendedCount() {
        prefs.edit().putInt(KEY_ATTENDED_COUNT, getAttendedCount() + 1).apply()
    }

    fun incrementFriendsCount() {
        prefs.edit().putInt(KEY_FRIENDS_COUNT, getFriendsCount() + 1).apply()
    }

    companion object {
        private const val PREFS_NAME = "spacer_session"
        private const val KEY_LOGGED_IN = "logged_in"
        private const val KEY_PROFILE_NAME = "profile_name"
        private const val KEY_ABOUT_ME = "about_me"
        private const val KEY_PROFILE_IMAGE_URI = "profile_image_uri"
        private const val KEY_LOCATION_LABEL = "location_label"
        private const val KEY_HOSTED_COUNT = "hosted_count"
        private const val KEY_ATTENDED_COUNT = "attended_count"
        private const val KEY_FRIENDS_COUNT = "friends_count"
    }
}
