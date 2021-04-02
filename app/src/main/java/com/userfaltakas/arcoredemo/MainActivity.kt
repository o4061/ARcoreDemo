package com.userfaltakas.arcoredemo

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.ar.core.Anchor
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.CompletableFuture

private const val DOUBLE_TAP_TOLERANCE_MS = 1000L

class MainActivity : AppCompatActivity() {

    lateinit var arFragment: ArFragment

    private val models = mutableListOf(
        Model(R.drawable.chair, "Chair", R.raw.chair),
        Model(R.drawable.oven, "Oven", R.raw.oven),
        Model(R.drawable.piano, "Piano", R.raw.piano),
        Model(R.drawable.table, "Table", R.raw.table)
    )

    private lateinit var selectedModel: Model

    private val viewNodes = mutableListOf<Node>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arFragment = fragment as ArFragment
        setupBottomSheet()
        setUpRecyclerView()

        setupDoubleTapArPlaneListener()

        getCurrentScene().addOnUpdateListener {
            rotateViewNodesTowardsUser()
        }
    }

    private fun setupDoubleTapArPlaneListener() {
        var firstTapTime = 0L
        arFragment.setOnTapArPlaneListener { hitResult, _, _ ->
            when {
                firstTapTime == 0L -> {
                    firstTapTime = System.currentTimeMillis()
                }
                System.currentTimeMillis() - firstTapTime < DOUBLE_TAP_TOLERANCE_MS -> {
                    firstTapTime = 0L
                    loadModel { modelRenderable, viewRenderable ->
                        addNoteToScreen(hitResult.createAnchor(), modelRenderable, viewRenderable)
                    }
                }
                else -> {
                    firstTapTime = System.currentTimeMillis()
                }
            }
        }
    }

    private fun setUpRecyclerView() {
        rvModels.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvModels.adapter = ModelAdapter(models).apply {
            selectedModel.observe(this@MainActivity, {
                this@MainActivity.selectedModel = it
                val newTitle = "Models (${it.title})"
                tvModel.text = newTitle
            })
        }
    }

    private fun setupBottomSheet() {
        val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.peekHeight =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                20f,
                resources.displayMetrics
            ).toInt()
        bottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                bottomSheet.bringToFront()
            }
        })
    }


    private fun getCurrentScene() = arFragment.arSceneView.scene

    private fun createsDeleteButton(): Button {
        return Button(this).apply {
            text = "Delete"
            setBackgroundColor(Color.RED)
            setTextColor(Color.WHITE)
        }
    }

    private fun rotateViewNodesTowardsUser() {
        for (node in viewNodes) {
            node.renderable?.let {
                val camPos = getCurrentScene().camera.worldPosition
                val viewNodePos = node.worldPosition
                val dir = Vector3.subtract(camPos, viewNodePos)
                node.worldRotation = Quaternion.lookRotation(dir, Vector3.up())
            }
        }
    }

    private fun addNoteToScreen(
        anchor: Anchor,
        modelRenderable: ModelRenderable,
        viewRenderable: ViewRenderable
    ) {
        val anchorNode = AnchorNode(anchor)
        val modelNode = TransformableNode(arFragment.transformationSystem).apply {
            renderable = modelRenderable
            setParent(anchorNode)
            getCurrentScene().addChild(anchorNode)
            select()
        }

        val viewNode = Node().apply {
            renderable = null
            setParent(modelNode)
            val box = modelNode.renderable?.collisionShape as com.google.ar.sceneform.collision.Box
            localPosition = Vector3(0f, box.size.y, 0f)
            (viewRenderable.view as Button).setOnClickListener {
                getCurrentScene().removeChild(anchorNode)
                viewNodes.remove(this)
            }
        }
        viewNodes.add(viewNode)
        modelNode.setOnTapListener { _, _ ->
            if (!modelNode.isTransforming) {
                if (viewNode.renderable == null) {
                    viewNode.renderable = viewRenderable
                } else {
                    viewNode.renderable = null
                }
            }
        }

    }

    private fun loadModel(callback: (ModelRenderable, ViewRenderable) -> Unit) {
        val modelRenderable = ModelRenderable.builder()
            .setSource(this, selectedModel.modelResourceId)
            .build()

        val viewRenderable = ViewRenderable.builder()
            .setView(this, createsDeleteButton())
            .build()
        CompletableFuture.allOf(modelRenderable, viewRenderable)
            .thenAccept {
                callback(modelRenderable.get(), viewRenderable.get())
            }
            .exceptionally {
                Toast.makeText(this, "Error loading model: $it", Toast.LENGTH_SHORT).show()
                null
            }
    }
}