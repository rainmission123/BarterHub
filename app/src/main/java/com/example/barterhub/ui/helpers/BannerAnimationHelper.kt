package com.example.barterhub.ui.helpers

import android.view.View

object BannerAnimationHelper {

    fun startShimmerEffect(banner: View, shimmer: View, isAlive: () -> Boolean) {
        shimmer.post {
            val parentWidth = banner.width.toFloat()

            shimmer.animate()
                .translationX(parentWidth)
                .setDuration(1500)
                .withEndAction {
                    shimmer.translationX = -200f

                    if (isAlive()) {
                        shimmer.postDelayed({
                            startShimmerEffect(banner, shimmer, isAlive)
                        }, 2000)
                    }
                }
                .start()
        }
    }

    fun startPulse(view: View, isAlive: () -> Boolean) {
        view.animate()
            .scaleX(1.02f)
            .scaleY(1.02f)
            .setDuration(900)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(900)
                    .withEndAction {
                        if (isAlive()) {
                            startPulse(view, isAlive)
                        }
                    }
                    .start()
            }
            .start()
    }
}