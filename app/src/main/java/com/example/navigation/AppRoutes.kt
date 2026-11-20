package com.example.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.example.data.model.UserRole

/**
 * Root navigation graph routes connecting Portal Selection, dedicated Role Login screens,
 * and authenticated Role Dashboard shells.
 */
object AppRoutes {
    const val AUTH_GRAPH = "auth_graph"
    const val PORTAL_SELECTOR = "auth/portal_selector"
    const val ADMIN_LOGIN = "auth/login/admin"
    const val TEACHER_LOGIN = "auth/login/teacher"
    const val STUDENT_LOGIN = "auth/login/student"

    const val SESSION_LOADING = "session/loading"

    const val ADMIN_DASHBOARD = "dashboard/admin"
    const val TEACHER_DASHBOARD = "dashboard/teacher"
    const val STUDENT_DASHBOARD = "dashboard/student"

    fun loginRouteFor(role: UserRole): String = when (role) {
        UserRole.ADMIN -> ADMIN_LOGIN
        UserRole.TEACHER -> TEACHER_LOGIN
        UserRole.STUDENT -> STUDENT_LOGIN
    }

    fun dashboardRouteFor(role: UserRole): String = when (role) {
        UserRole.ADMIN -> ADMIN_DASHBOARD
        UserRole.TEACHER -> TEACHER_DASHBOARD
        UserRole.STUDENT -> STUDENT_DASHBOARD
    }

    fun isAuthRoute(route: String?): Boolean = route == PORTAL_SELECTOR ||
        route == ADMIN_LOGIN ||
        route == TEACHER_LOGIN ||
        route == STUDENT_LOGIN ||
        route == AUTH_GRAPH
}

/**
 * Internal routes for the Super Admin Dashboard NavHost.
 */
object AdminRoutes {
    const val OVERVIEW = "admin/overview"
    const val PEOPLE = "admin/people"
    const val PAPERS_BANK = "admin/papers_bank"
    const val EXAMS = "admin/exams"
    const val RESULTS_REPORTS = "admin/results_reports"

    const val ARG_RESULT_ID = "resultId"
    const val RESULT_ANALYSIS = "admin/result_analysis/{$ARG_RESULT_ID}"

    fun resultAnalysis(resultId: String): String =
        "admin/result_analysis/${Uri.encode(resultId)}"

    fun isDetailRoute(route: String?): Boolean =
        route?.startsWith("admin/result_analysis") == true
}

/**
 * Internal routes for the Teacher Dashboard NavHost.
 */
object TeacherRoutes {
    const val OVERVIEW = "teacher/overview"
    const val STUDENTS = "teacher/students"
    const val ASSIGN_EXAMS = "teacher/assign_exams"
    const val RESULTS = "teacher/results"

    const val ARG_RESULT_ID = "resultId"
    const val RESULT_ANALYSIS = "teacher/result_analysis/{$ARG_RESULT_ID}"

    fun resultAnalysis(resultId: String): String =
        "teacher/result_analysis/${Uri.encode(resultId)}"

    fun isDetailRoute(route: String?): Boolean =
        route?.startsWith("teacher/result_analysis") == true
}

/**
 * Internal routes for the Student Dashboard NavHost.
 */
object StudentRoutes {
    const val DASHBOARD = "student/dashboard"
    const val EXAMS = "student/exams"
    const val PROGRESS = "student/progress"

    const val ARG_EXAM_ID = "examId"
    const val EXAM_SESSION = "student/exam_session/{$ARG_EXAM_ID}"

    const val ARG_RESULT_ID = "resultId"
    const val RESULT_ANALYSIS = "student/result_analysis/{$ARG_RESULT_ID}"

    fun examSession(examId: String): String =
        "student/exam_session/${Uri.encode(examId)}"

    fun resultAnalysis(resultId: String): String =
        "student/result_analysis/${Uri.encode(resultId)}"

    fun isFullScreenRoute(route: String?): Boolean =
        route?.startsWith("student/exam_session") == true ||
            route?.startsWith("student/result_analysis") == true
}

/**
 * Navigates between top-level dashboard tabs while preserving scroll/UI state per tab
 * and preventing duplicate backstack entries.
 */
fun NavController.navigateToTopLevelTab(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Shared Material 3 motion transitions for NavHost graphs.
 */
object NavTransitions {
    private const val DURATION_MS = 240

    fun slideInForward(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition =
        scope.slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(DURATION_MS)
        ) + fadeIn(animationSpec = tween(DURATION_MS))

    fun slideOutForward(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition =
        scope.slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(DURATION_MS)
        ) + fadeOut(animationSpec = tween(DURATION_MS))

    fun slideInBackward(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition =
        scope.slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.End,
            animationSpec = tween(DURATION_MS)
        ) + fadeIn(animationSpec = tween(DURATION_MS))

    fun slideOutBackward(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition =
        scope.slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.End,
            animationSpec = tween(DURATION_MS)
        ) + fadeOut(animationSpec = tween(DURATION_MS))

    val tabFadeIn: EnterTransition = fadeIn(animationSpec = tween(180))
    val tabFadeOut: ExitTransition = fadeOut(animationSpec = tween(160))
}
