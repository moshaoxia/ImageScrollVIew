package com.moshaoxia.imageccrollview

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    private var orientation = ScrollImageLayout.ORIENTATION_HORIZONTAL
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btn_start.setOnClickListener {
            fl_main.startScroll()
        }
        btn_stop.setOnClickListener {
            fl_main.stopScroll()
        }
        btn_change.setOnClickListener {
            if (orientation == ScrollImageLayout.ORIENTATION_HORIZONTAL) {
                orientation = ScrollImageLayout.ORIENTATION_VERTICAL
            } else {
                orientation = ScrollImageLayout.ORIENTATION_HORIZONTAL
            }
            fl_main.setScrollOrientation(orientation)
        }
        btn_mask.setOnClickListener {
            fl_main.setMaskDrawable(resources.getDrawable(R.drawable.friend_cast_mask_drawable))
        }
    }
}