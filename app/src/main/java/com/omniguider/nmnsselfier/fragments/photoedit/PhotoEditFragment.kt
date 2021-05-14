package com.omniguider.nmnsselfier.fragments.photoedit

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.core.util.forEach
import androidx.core.util.set
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.material.slider.Slider
import com.omniguider.nmnsselfier.R
import com.omniguider.nmnsselfier.cameratrinket.MyStickerView
import com.omniguider.nmnsselfier.databinding.ColorItemBinding
import com.omniguider.nmnsselfier.databinding.FragmentPhoteEditBinding
import com.omniguider.nmnsselfier.databinding.FrameItemBinding
import com.omniguider.nmnsselfier.utils.ConstantUtil
import com.omniguider.nmnsselfier.utils.loadBitmapFromView
import com.omniguider.nmnsselfier.utils.mixBitmap

class PhotoEditFragment : Fragment() {

    private val dTag = javaClass.simpleName

    private lateinit var binding: FragmentPhoteEditBinding

    private lateinit var frameSelectionStatus: ArrayList<FrameSelectionStatus>

    private lateinit var colorSelectionStatus: ArrayList<ColorSelectionStatus>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_phote_edit, container, false)
        binding.showFunSelector = true
        binding.showFrameSelector = false
        binding.showTrinketSelector = false
        binding.showFingerDrawer = false
        binding.frameDrawableRes = ConstantUtil.NONE_FRAME
        binding.trinketWasAdd = false
        val tmp =
            arguments?.getParcelableArrayList<FrameSelectionStatus>(ConstantUtil.KEY_FRAME_SELECTION_STATUS)
                ?.let {
                    it.firstOrNull { it1 ->
                        it1.selected == true
                    }?.drawableRes?.also { res ->
                        setFrame(res)
                    }
                    it
                }
        frameSelectionStatus = tmp
            ?: arrayListOf(
                FrameSelectionStatus(R.drawable.frame1),
                FrameSelectionStatus(R.drawable.frame2),
                FrameSelectionStatus(R.drawable.frame3),
            )
        colorSelectionStatus = arrayListOf(
            ColorSelectionStatus(Color.parseColor("#f44336"), true),
            ColorSelectionStatus(Color.parseColor("#E91E63")),
            ColorSelectionStatus(Color.parseColor("#9C27B0")),
            ColorSelectionStatus(Color.parseColor("#673AB7")),
            ColorSelectionStatus(Color.parseColor("#3F51B5")),
            ColorSelectionStatus(Color.parseColor("#2196F3")),
            ColorSelectionStatus(Color.parseColor("#03A9F4")),
            ColorSelectionStatus(Color.parseColor("#00BCD4")),
            ColorSelectionStatus(Color.parseColor("#009688")),
            ColorSelectionStatus(Color.parseColor("#4CAF50")),
            ColorSelectionStatus(Color.parseColor("#8BC34A")),
            ColorSelectionStatus(Color.parseColor("#CDDC39")),
            ColorSelectionStatus(Color.parseColor("#FFEB3B")),
            ColorSelectionStatus(Color.parseColor("#FFC107")),
            ColorSelectionStatus(Color.parseColor("#FF9800")),
            ColorSelectionStatus(Color.parseColor("#FF5722")),
            ColorSelectionStatus(Color.parseColor("#795548")),
            ColorSelectionStatus(Color.parseColor("#9E9E9E")),
            ColorSelectionStatus(Color.parseColor("#607D8B")),
            ColorSelectionStatus(Color.parseColor("#212121"))
        )

        binding.cameraFrameSelector.frameList.apply {
            (layoutManager as LinearLayoutManager).orientation = LinearLayoutManager.HORIZONTAL
            adapter = FrameListAdapter(frameSelectionStatus)
        }
        /*binding.cameraFingerDrawerController.colorList.apply {
            (layoutManager as FlexboxLayoutManager).flexDirection = FlexDirection.ROW
            adapter = ColorListAdapter(colorSelectionStatus)
        }*/
        initColorSelector()
        binding.cameraFingerDrawerController.widthSelector.apply {
            addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {
                    binding.fingerDrawer.drawable = false
                }

                override fun onStopTrackingTouch(slider: Slider) {
                    val range = slider.valueTo - slider.valueFrom
                    binding.fingerDrawer.setPathWidth(value / range)
                    binding.fingerDrawer.drawable = true
                }
            })
            addOnChangeListener { slider, value, fromUser ->
                binding.widthInfo = getString(R.string.string_width_x, value.toInt())
            }
            value = 3f
            binding.widthInfo = getString(R.string.string_width_x, value.toInt())
        }
        binding.onClickListener = onClickListener
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.getParcelable<Bitmap?>(ConstantUtil.KEY_BASE_PHOTO)?.let {
            binding.base = it
            binding.tmpPhoto.setImageBitmap(it)
        }
    }

    private val onClickListener = View.OnClickListener {
        when (it) {
            binding.cameraFunSelector.clean -> {
                binding.base = null
                binding.tmpPhoto.setImageBitmap(null)
                removeFrame()
                binding.root.findViewById<FrameLayout>(R.id.trinket_pool).removeAllViews()
                binding.fingerDrawer.onClear()
                //binding.fingerDrawer.setErase(true)
                cleanAllTrinket()
                binding.showFunSelector = false
                binding.showClean = false
                binding.showFrameSelector = false
                binding.showTrinketSelector = false
                binding.showFingerDrawer = false
                findNavController().popBackStack()
            }
            binding.cameraFunSelector.mix -> {
                Thread {
                    val result = mixBitmap(
                        loadBitmapFromView(binding.baseLayout),
                        binding.fingerDrawer.getViewBitmap()
                    )
                    binding.tmpPhoto.post {
                        binding.lastResultPhoto.setImageBitmap(result)
                        //binding.root.findViewById<View>(R.id.clean).callOnClick()
                    }
                }.start()
            }
            binding.cameraFunSelector.openFrameSelector -> {
                //update frame list in frame selector
                /*binding.root.findViewById<RecyclerView>(R.id.frameList).adapter =
                    FrameListAdapter(frameSelectionStatus)*/
                binding.showFrameSelector = true
            }
            binding.cameraFunSelector.openTrinketSelector -> binding.showTrinketSelector = true
            binding.cameraFunSelector.openFingerDrawer -> {
                binding.showFingerDrawer = true
                binding.fingerDrawer.drawable = true
            }
            binding.cameraFrameSelector.clearFrame -> removeFrame()
            binding.cameraFrameSelector.backFrameSelector -> binding.showFrameSelector = false
            binding.cameraTrinketSelector.trinket1 -> addTrinket(R.drawable.crown)
            binding.cameraTrinketSelector.trinket2 -> addTrinket(R.drawable.money)
            binding.cameraTrinketSelector.clearTrinket -> cleanAllTrinket()
            binding.cameraTrinketSelector.backTrinketSelector -> binding.showTrinketSelector = false
            binding.cameraFingerDrawerController.backFingerDrawer -> {
                binding.fingerCanvasWasDraw = binding.fingerDrawer.wasDraw
                binding.showFingerDrawer = false
                binding.fingerDrawer.drawable = false
            }
            binding.cameraFingerDrawerController.eraseFingerDrawer -> binding.fingerDrawer.onClear()
        }
    }

    private fun setFrame(@DrawableRes drawable: Int) {
        if (binding.frameDrawableRes != drawable) {
            binding.frame.setImageResource(drawable)
            binding.frameDrawableRes = drawable
        } else removeFrame()
    }

    private fun removeFrame() {
        Log.d(dTag, "binding.frameDrawableRes:${binding.frameDrawableRes}")
        if (binding.frameDrawableRes != ConstantUtil.NONE_FRAME) {
            binding.frame.setImageDrawable(null)
            binding.frameDrawableRes = ConstantUtil.NONE_FRAME
            frameSelectionStatus.forEach {
                it.selected = false
            }
            binding.root.findViewById<RecyclerView>(R.id.frameList).adapter?.also {
                it as FrameListAdapter
                it.cleanSelected()
            }
        }
    }

    private fun addTrinket(@DrawableRes drawable: Int) {
        MyStickerView(requireContext()).apply {
            id = View.generateViewId()
            setImageResource(drawable)
            setDeleteOnClick { cleanTrinket(this) }
            binding.trinketPool.addView(this)
            if (!binding.trinketWasAdd!!) binding.trinketWasAdd = true
        }
    }

    private fun cleanTrinket(v: View) {
        if (binding.trinketPool.indexOfChild(v) != -1) {
            binding.trinketPool.post {
                binding.trinketPool.removeView(v)
                if (binding.trinketPool.childCount == 0 && binding.trinketWasAdd!!)
                    binding.trinketWasAdd = false
            }
        }
    }

    private fun cleanAllTrinket() {
        if (binding.trinketPool.childCount > 0)
            binding.trinketPool.post {
                binding.trinketPool.removeAllViews()
                binding.trinketWasAdd = false
            }
    }

    private fun initColorSelector() {
        binding.cameraFingerDrawerController.colorList.apply {
            removeAllViews()
            val bindings = SparseArray<ColorItemBinding>()
            colorSelectionStatus.forEachIndexed { index, info ->
                fun updateNewSelected(selectedPosition: Int) {
                    colorSelectionStatus.forEachIndexed { index, colorSelectionStatus ->
                        colorSelectionStatus.selected = index == selectedPosition
                    }
                    bindings.forEach { index, binding ->
                        binding.selected = index == selectedPosition
                    }
                    this@PhotoEditFragment.binding.fingerDrawer.setPathColor(colorSelectionStatus[selectedPosition].color)
                }

                fun initItem(pos: Int): View {
                    val binding = DataBindingUtil.inflate<ColorItemBinding>(
                        LayoutInflater.from(context),
                        R.layout.color_item,
                        this,
                        false
                    )
                    bindings[pos] = binding
                    binding.selected = info.selected
                    binding.colorType.setBackgroundColor(info.color)
                    binding.root.setOnClickListener {
                        if (info.selected!!) return@setOnClickListener
                        updateNewSelected(index)
                    }
                    return binding.root
                }
                addView(initItem(index))
            }
        }
    }

    inner class FrameListAdapter(private val frameDrawableRes: ArrayList<FrameSelectionStatus>) :
        RecyclerView.Adapter<FrameListAdapter.FrameListHolder>() {
        inner class FrameListHolder constructor(val binding: FrameItemBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(position: Int) {
                binding.isClearOption = position == 0
                if (binding.isClearOption!!) {
                    binding.clearFrame.setOnClickListener {
                        cleanSelected()
                        removeFrame()
                    }
                } else {
                    val index = position - 1
                    binding.selected = frameDrawableRes[index].selected
                    binding.frame.setImageResource(frameDrawableRes[index].drawableRes)
                    binding.root.setOnClickListener {
                        cleanSelected(position)
                        binding.selected = !binding.selected!!
                        frameDrawableRes[index].selected = binding.selected
                        if (binding.selected!!) {
                            setFrame(frameDrawableRes[position - 1].drawableRes)
                        } else removeFrame()
                    }
                }
            }
        }

        private val allBinding = SparseArray<FrameItemBinding>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FrameListHolder =
            FrameListHolder(
                DataBindingUtil.inflate<FrameItemBinding>(
                    LayoutInflater.from(parent.context),
                    viewType,
                    parent,
                    false
                ).apply {
                    selected = false
                }
            )

        override fun onBindViewHolder(holder: FrameListHolder, position: Int) =
            holder.bind(position).apply {
                allBinding[position] = holder.binding
            }

        override fun getItemCount(): Int = frameDrawableRes.size + 1

        override fun getItemViewType(position: Int): Int = R.layout.frame_item

        fun cleanSelected(ignore: Int? = null) {
            allBinding.forEach { key, value ->
                if (ignore != null) {
                    if (key != ignore) value.selected = false
                } else value.selected = false
            }
        }
    }

    inner class ColorListAdapter(private val colorList: ArrayList<ColorSelectionStatus>) :
        RecyclerView.Adapter<ColorListAdapter.ColorListHolder>() {

        private val bindings = SparseArray<ColorItemBinding>()

        inner class ColorListHolder constructor(val binding: ColorItemBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(position: Int) {
                bindings[position] = binding
                val info = colorList[position]
                binding.selected = info.selected
                binding.colorType.setBackgroundColor(info.color)
                binding.root.setOnClickListener {
                    if (info.selected!!) return@setOnClickListener
                    updateNewSelected(position)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorListHolder =
            ColorListHolder(
                DataBindingUtil.inflate(LayoutInflater.from(context), viewType, parent, false)
            )

        override fun onBindViewHolder(holder: ColorListHolder, position: Int) =
            holder.bind(position)

        override fun getItemViewType(position: Int): Int = R.layout.color_item

        override fun getItemCount(): Int = colorList.size

        private fun updateNewSelected(selectedPosition: Int) {
            colorList.forEachIndexed { index, colorSelectionStatus ->
                colorSelectionStatus.selected = index == selectedPosition
            }
            bindings.forEach { index, binding ->
                binding.selected = index == selectedPosition
            }
            this@PhotoEditFragment.binding.fingerDrawer.setPathColor(colorList[selectedPosition].color)
        }
    }

}