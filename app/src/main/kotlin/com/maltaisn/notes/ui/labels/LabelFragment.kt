/*
 * Copyright 2021 Nicolas Maltais
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.maltaisn.notes.ui.labels

import android.os.Bundle
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.maltaisn.notes.App
import com.maltaisn.notes.navigateSafe
import com.maltaisn.notes.sync.R
import com.maltaisn.notes.sync.databinding.FragmentLabelBinding
import com.maltaisn.notes.ui.common.ConfirmDialog
import com.maltaisn.notes.ui.labels.adapter.LabelAdapter
import com.maltaisn.notes.ui.observeEvent
import com.maltaisn.notes.ui.utils.startSafeActionMode
import com.maltaisn.notes.ui.viewModel
import java.text.NumberFormat
import javax.inject.Inject

/**
 * This fragment has two purposes:
 *
 * - Managing labels: add, rename, delete. Supports multiple selection.
 * - Selecting and applying labels: set or change labels on a set of notes.
 *
 * The mode is determined by the argument passed by the navigation component.
 */
class LabelFragment : DialogFragment(), Toolbar.OnMenuItemClickListener,
    ActionMode.Callback, ConfirmDialog.Callback {

    @Inject
    lateinit var viewModelFactory: LabelViewModel.Factory
    val viewModel by viewModel { viewModelFactory.create(it) }

    private val args: LabelFragmentArgs by navArgs()

    private var _binding: FragmentLabelBinding? = null
    private val binding get() = _binding!!

    private var actionMode: ActionMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireContext().applicationContext as App).appComponent.inject(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?
    ): View {
        _binding = FragmentLabelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()

        viewModel.start(args.noteIds.toList())

        binding.toolbar.apply {
            setOnMenuItemClickListener(this@LabelFragment)
            setNavigationIcon(R.drawable.ic_arrow_start)
            setNavigationOnClickListener {
                findNavController().popBackStack()
            }
            setTitle(if (args.noteIds.isEmpty()) {
                R.string.label_manage
            } else {
                R.string.label_select
            })
            menu.findItem(R.id.item_confirm).isVisible = (args.noteIds.isNotEmpty())
        }

        binding.fab.setOnClickListener {
            findNavController().navigateSafe(LabelFragmentDirections.actionLabelToLabelEdit())
        }

        val rcv = binding.recyclerView
        rcv.setHasFixedSize(true)
        val adapter = LabelAdapter(context, viewModel)
        val layoutManager = LinearLayoutManager(context)
        rcv.adapter = adapter
        rcv.layoutManager = layoutManager

        setupViewModelObservers(adapter)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupViewModelObservers(adapter: LabelAdapter) {
        viewModel.labelItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
        }

        viewModel.placeholderShown.observe(viewLifecycleOwner) { shown ->
            binding.placeholderGroup.isVisible = shown
        }

        viewModel.labelSelection.observe(viewLifecycleOwner) { count ->
            updateActionModeForSelection(count)
            updateItemsForSelection(count)
            updateFabForSelection(count)
        }

        viewModel.showRenameDialogEvent.observeEvent(viewLifecycleOwner) { labelId ->
            findNavController().navigateSafe(LabelFragmentDirections.actionLabelToLabelEdit(labelId))
        }

        viewModel.showDeleteConfirmEvent.observeEvent(viewLifecycleOwner) {
            showDeleteConfirmDialog()
        }

        viewModel.exitEvent.observeEvent(viewLifecycleOwner) {
            findNavController().popBackStack()
        }
    }

    private fun updateActionModeForSelection(count: Int) {
        if (count != 0 && actionMode == null) {
            actionMode = binding.toolbar.startSafeActionMode(this)
        } else if (count == 0 && actionMode != null) {
            actionMode?.finish()
            actionMode = null
        }
    }

    private fun updateItemsForSelection(count: Int) {
        actionMode?.let {
            it.title = NUMBER_FORMAT.format(count)

            val menu = it.menu
            menu.findItem(R.id.item_rename).isVisible = count == 1
        }
    }

    private fun updateFabForSelection(count: Int) {
        if (count != 0) {
            if (binding.fab.isOrWillBeShown) {
                binding.fab.hide()
            }
        } else if (binding.fab.isOrWillBeHidden) {
            binding.fab.show()
        }
    }

    private fun showDeleteConfirmDialog() {
        ConfirmDialog.newInstance(
            title = R.string.action_delete_selection,
            message = R.string.label_delete_message,
            btnPositive = R.string.action_delete
        ).show(childFragmentManager, DELETE_CONFIRM_DIALOG_TAG)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.item_confirm -> viewModel.setNotesLabels()
            else -> return false
        }
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.item_rename -> viewModel.renameSelection()
            R.id.item_delete -> viewModel.deleteSelectionPre()
            R.id.item_select_all -> viewModel.selectAll()
            else -> return false
        }
        return true
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.cab_label_selection, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

    override fun onDestroyActionMode(mode: ActionMode) {
        actionMode = null
        viewModel.clearSelection()
    }

    override fun onDialogPositiveButtonClicked(tag: String?) {
        if (tag == DELETE_CONFIRM_DIALOG_TAG) {
            viewModel.deleteSelection()
        }
    }

    companion object {
        private val NUMBER_FORMAT = NumberFormat.getInstance()

        private const val DELETE_CONFIRM_DIALOG_TAG = "delete_confirm_dialog"
    }

}
