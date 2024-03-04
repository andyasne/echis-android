package org.commcare.androidTests

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.commcare.annotations.BrowserstackTests
import org.commcare.dalvik.eCHIS.R
import org.commcare.utils.InstrumentationUtility
import org.commcare.utils.doesNotExist
import org.commcare.utils.isDisplayed
import org.hamcrest.Matchers.endsWith
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
@BrowserstackTests
class CaseSharingTest: BaseTest() {

    companion object {
        const val CCZ_NAME = "case_sharing.ccz"
        const val APP_NAME = "Case Sharing Test"
    }

    @Before
    fun setup() {
        installApp(APP_NAME, CCZ_NAME)
    }

    @Test
    fun testCaseSharing() {
        InstrumentationUtility.login("case_sharing_1", "123")
        caseCleanup()

        // Create case with first user
        createCase("First Case", "1")

        // validate that case was created
        InstrumentationUtility.openModule("Follow Up")
        withText("First Case").isDisplayed()

        // logout first user.
        InstrumentationUtility.logout()

        // Create case with second user.
        InstrumentationUtility.login("case_sharing_2", "123")
        createCase("Second Case", "2")

        // Update user 1's case
        // validate that case was created
        InstrumentationUtility.openModule("Follow Up")
        withText("First Case").isDisplayed()
        onView(withText("First Case"))
                .perform(click())
        withText("1").isDisplayed()
        onView(withText("Continue"))
                .perform(click())
        onView(withText("Visit"))
                .perform(click())
        onView(withId(R.id.nav_btn_next))
                .perform(click())
        withText("1").isDisplayed()
        onView(withClassName(endsWith("EditText")))
                .perform(typeText("2"))
        onView(withId(R.id.nav_btn_finish))
                .perform(click())
        onView(withText("Sync with Server"))
                .perform(click())

        // Logout second user
        InstrumentationUtility.logout()

        // Login with first user and close cases.
        InstrumentationUtility.login("case_sharing_1", "123")
        onView(withText("Sync with Server"))
                .perform(click())

        InstrumentationUtility.openModule("Follow Up")
        withText("First Case").isDisplayed()
        withText("Second Case").isDisplayed()

        onView(withText("First Case"))
                .perform(click())
        withText("12").isDisplayed()
        closeCase()

        InstrumentationUtility.openModule("Follow Up")
        withText("First Case").doesNotExist()
        withText("Second Case").isDisplayed()

        onView(withText("Second Case"))
                .perform(click())
        withText("2").isDisplayed()
        closeCase()

        onView(withText("Sync with Server"))
                .perform(click())

        // logout first user.
        InstrumentationUtility.logout()

        // login with second user and validate that the cases are closed.
        InstrumentationUtility.login("case_sharing_2", "123")
        onView(withText("Sync with Server"))
                .perform(click())

        InstrumentationUtility.openModule("Follow Up")
        withText("First Case").doesNotExist()
        withText("Second Case").doesNotExist()
    }

    private fun caseCleanup() {
        InstrumentationUtility.openModule("Test Cleanup")
        onView(withId(R.id.nav_btn_finish))
                .perform(click())
        InstrumentationUtility.openModule("Follow Up")
        withText("First Case").doesNotExist()
        withText("Second Case").doesNotExist()
        InstrumentationUtility.gotoHome()
        onView(withText("Sync with Server"))
                .perform(click())
    }

    private fun createCase(name: String, number: String) {
        InstrumentationUtility.openModule("Registration")

        onView(withClassName(endsWith("EditText")))
                .perform(typeText(name))
        onView(withId(R.id.nav_btn_next))
                .perform(click())
        onView(withClassName(endsWith("EditText")))
                .perform(typeText(number))
        onView(withId(R.id.nav_btn_finish))
                .perform(click())

        onView(withText("Sync with Server"))
                .perform(click())
    }

    private fun closeCase() {
        onView(withText("Continue"))
                .perform(click())
        onView(withText("Close"))
                .perform(click())
        onView(withId(R.id.nav_btn_finish))
                .perform(click())
    }
}
