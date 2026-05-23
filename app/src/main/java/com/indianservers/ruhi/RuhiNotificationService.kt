package com.indianservers.ruhi

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class RuhiNotificationService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val category = sbn.notification.category
        val reaction = when (category) {
            android.app.Notification.CATEGORY_MESSAGE,
            android.app.Notification.CATEGORY_SOCIAL -> RobotFaceView.Expression.HAPPY to "You got a message!"
            android.app.Notification.CATEGORY_ALARM -> RobotFaceView.Expression.WORRIED to "Your alarm went off!"
            android.app.Notification.CATEGORY_EMAIL -> RobotFaceView.Expression.CURIOUS to "New email arrived."
            android.app.Notification.CATEGORY_MISSED_CALL -> RobotFaceView.Expression.SAD to "You missed a call."
            else -> null
        }
        // Reactions are intentionally lightweight; MainActivity can observe persisted notification cues later.
        reaction ?: return
    }
}
