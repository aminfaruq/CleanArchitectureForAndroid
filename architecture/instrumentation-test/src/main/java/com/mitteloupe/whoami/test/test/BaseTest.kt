package com.mitteloupe.whoami.test.test

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.annotation.CallSuper
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.IdlingResource as ComposeIdlingResource
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource as EspressoIdlingResource
import androidx.test.platform.app.InstrumentationRegistry
import com.mitteloupe.whoami.test.localstore.KeyValueStore
import com.mitteloupe.whoami.test.rule.DisableAnimationsRule
import com.mitteloupe.whoami.test.rule.HiltInjectorRule
import com.mitteloupe.whoami.test.rule.LocalStoreRule
import com.mitteloupe.whoami.test.rule.ScreenshotFailureRule
import com.mitteloupe.whoami.test.rule.WebServerRule
import com.mitteloupe.whoami.test.server.MockDispatcher
import com.mitteloupe.whoami.test.server.MockWebServerProvider
import com.mitteloupe.whoami.test.server.ResponseStore
import dagger.hilt.android.testing.HiltAndroidRule
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

abstract class BaseTest {
    internal val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    internal open val hiltAndroidRule by lazy { HiltAndroidRule(this) }

    @Inject
    lateinit var mockDispatcher: MockDispatcher

    @Inject
    lateinit var mockWebServerProvider: MockWebServerProvider

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var responseStore: ResponseStore

    @Inject
    lateinit var keyValueStore: KeyValueStore

    @Inject
    lateinit var espressoIdlingResources: @JvmSuppressWildcards Collection<EspressoIdlingResource>

    @Inject
    lateinit var composeIdlingResources: @JvmSuppressWildcards Collection<ComposeIdlingResource>

    private val webServerRule by lazy {
        WebServerRule(
            lazy { listOf(mockDispatcher) },
            lazy { responseStore }
        ) { mockWebServerProvider.serverUrl }
    }

    private val localStoreRule by lazy {
        LocalStoreRule(
            lazy { sharedPreferences },
            lazy { keyValueStore }
        )
    }

    @get:Rule
    open val testRules: RuleChain by lazy {
        RuleChain
            .outerRule(hiltAndroidRule)
            .around(DisableAnimationsRule())
            .around(HiltInjectorRule(hiltAndroidRule))
            .around(ScreenshotFailureRule())
            .around(webServerRule)
            .around(localStoreRule)
            .around(composeTestRule)
    }

    abstract val composeTestRule: ComposeContentTestRule

    abstract val startActivityLauncher: AppLauncher

    @Before
    @CallSuper
    open fun setUp() {
        registerIdlingResources()
        startActivityLauncher.launch()
    }

    private fun registerIdlingResources() {
        val idlingRegistry = IdlingRegistry.getInstance()
        idlingRegistry.register(*(espressoIdlingResources).toTypedArray())
        composeIdlingResources.forEach(composeTestRule::registerIdlingResource)
    }

    sealed class AppLauncher {
        abstract fun launch()

        data class FromIntent(private val intent: Intent) : AppLauncher() {
            override fun launch() {
                ActivityScenario.launch<Activity>(intent)
            }
        }

        data class FromClass<ACTIVITY : Activity>(
            private val activityClass: Class<out ACTIVITY>
        ) : AppLauncher() {
            @Suppress("UNCHECKED_CAST")
            override fun launch() {
                ActivityScenario.launch(activityClass) as ActivityScenario<Activity>
            }
        }

        data class FromComposable(
            private val composeContentTestRule: ComposeContentTestRule,
            private val composable: @Composable () -> Unit
        ) : AppLauncher() {
            override fun launch() {
                @Suppress("UNCHECKED_CAST")
                val androidComposeTestRule =
                    composeContentTestRule as AndroidComposeTestRule<TestRule, ComponentActivity>
                val activity = androidComposeTestRule.activity
                val root = activity.findViewById<ViewGroup>(android.R.id.content)
                if (root != null) {
                    runBlocking(Dispatchers.Main) {
                        root.removeAllViews()
                    }
                }
                composeContentTestRule.setContent(composable)
            }
        }
    }
}
