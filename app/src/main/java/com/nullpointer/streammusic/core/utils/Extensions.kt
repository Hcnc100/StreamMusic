package com.nullpointer.streammusic.core.utils

import android.app.Activity
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentContainerView
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputLayout

fun TextInputLayout.setHideErrorDoAfter() {
    editText?.let {
        it.doAfterTextChanged {
            if (!error.isNullOrEmpty()) {
                error = ""
            }
        }
    }
}

fun View.hide() {
    visibility = View.GONE
}

fun View.show() {
    visibility = View.VISIBLE
}

fun AppCompatActivity.getNavController(navHost: FragmentContainerView): NavController {
    val navHostFragment = supportFragmentManager.findFragmentById(navHost.id) as NavHostFragment
    return navHostFragment.navController
}

fun Activity.hideKeyboard() {
    val imm: InputMethodManager =
        getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    //Find the currently focused view, so we can grab the correct window token from it.
    var view = currentFocus
    //If no view currently has focus, create a new one, just so we can grab a window token from it
    if (view == null) {
        view = View(this)
    }
    imm.hideSoftInputFromWindow(view.windowToken, 0)
}

fun Fragment.showSnack(message: String, viewOver: View? = null) {
    Snackbar.make(
        requireView(),
        message,
        Snackbar.LENGTH_SHORT
    ).also { snack ->
        snack.anchorView = viewOver
    }.show()
}
