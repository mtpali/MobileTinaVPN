package com.v2ray.ang.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import android.content.Intent
import android.widget.ImageView
import com.v2ray.ang.handler.ProfileActionPolicy
import com.v2ray.ang.handler.MobileTinaHiddenShareManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.util.Utils
import com.v2ray.ang.util.QRCodeDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.GridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.v2ray.ang.R
import com.v2ray.ang.contracts.MainAdapterListener
import com.v2ray.ang.databinding.FragmentGroupServerBinding
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.viewmodel.MainViewModel

/** Retained manual page; actions recheck persisted access before every operation. */
class GroupServerFragment : BaseFragment<FragmentGroupServerBinding>(), SwipeRefreshLayout.OnRefreshListener {
    private val ownerActivity: MainActivity get() = requireActivity() as MainActivity
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: MainRecyclerAdapter
    private val subId: String by lazy { arguments?.getString(ARG_SUB_ID).orEmpty() }

    companion object {
        private const val ARG_SUB_ID = "subscriptionId"
        fun newInstance(subId: String) = GroupServerFragment().apply {
            arguments = Bundle().apply { putString(ARG_SUB_ID, subId) }
        }
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentGroupServerBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = MainRecyclerAdapter(mainViewModel, ActivityAdapterListener())
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 1)
        addCustomDividerToRecyclerView(binding.recyclerView, R.drawable.custom_divider)
        binding.recyclerView.adapter = adapter
        binding.refreshLayout.setOnRefreshListener(this)

        mainViewModel.updateListAction.observe(viewLifecycleOwner) { index ->
            if (mainViewModel.subscriptionId == subId) {
                adapter.setData(mainViewModel.serversCache, index)
                ownerActivity.refreshSelectedServerUi()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.subscriptionIdChanged(subId)
        ownerActivity.refreshSelectedServerUi()
    }

    private fun setSelectServer(guid: String) {
        val selected = MmkvManager.getSelectServer()
        if (guid == selected) return
        MmkvManager.setSelectServer(guid)
        val fromPosition = mainViewModel.getPosition(selected.orEmpty())
        val toPosition = mainViewModel.getPosition(guid)
        adapter.setSelectServer(fromPosition, toPosition)
        ownerActivity.refreshSelectedServerUi()
        if (mainViewModel.isRunning.value == true) ownerActivity.restartV2Ray()
    }

    private inner class ActivityAdapterListener : MainAdapterListener {
        override fun onEdit(guid: String, position: Int) = Unit
        override fun onShare(url: String) = Unit
        override fun onRefreshData() = Unit
        override fun onRemove(guid: String, position: Int) {
            if (!ProfileActionPolicy.allowsProfile(guid)) return
            AlertDialog.Builder(ownerActivity)
                .setMessage(R.string.del_config_comfirm)
                .setNegativeButton(R.string.mobiletina_cancel, null)
                .setPositiveButton(R.string.mobiletina_confirm) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val removed = withContext(Dispatchers.IO) {
                            if (!ProfileActionPolicy.allowsProfile(guid)) return@withContext false
                            if (guid == MmkvManager.getSelectServer() && !V2RayServiceManager.stopAndAwait(requireContext().applicationContext)) return@withContext false
                            if (!ProfileActionPolicy.allowsProfile(guid)) return@withContext false
                            MmkvManager.removeServer(guid)
                            true
                        }
                        if (removed) ownerActivity.refreshServerGroups() else ownerActivity.toast(R.string.toast_failure)
                    }
                }.show()
        }
        override fun onEdit(guid: String, position: Int, profile: ProfileItem) = ownerActivity.editServer(guid)
        override fun onSelectServer(guid: String) = setSelectServer(guid)
        override fun onShare(guid: String, profile: ProfileItem, position: Int, more: Boolean) {
            if (!ProfileActionPolicy.allowsProfile(guid)) return
            AlertDialog.Builder(ownerActivity).setItems(arrayOf(
                getString(R.string.mobiletina_server_qr), getString(R.string.mobiletina_copy_server), getString(R.string.mobiletina_share_server)
            )) { _, choice ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val content = withContext(Dispatchers.IO) { MobileTinaHiddenShareManager.exportConfig(guid) } ?: return@launch
                    if (!ProfileActionPolicy.allowsProfile(guid)) return@launch
                    when (choice) {
                        0 -> {
                            val bitmap = withContext(Dispatchers.Default) { QRCodeDecoder.createQRCode(content) }
                            if (bitmap == null) { ownerActivity.toast(R.string.toast_failure); return@launch }
                            if (!ProfileActionPolicy.allowsProfile(guid)) return@launch
                            val image = ImageView(ownerActivity).apply { setImageBitmap(bitmap); adjustViewBounds = true }
                            AlertDialog.Builder(ownerActivity).setView(image).setNegativeButton(R.string.mobiletina_close, null).show()
                        }
                        1 -> Utils.setClipboard(ownerActivity, content)
                        2 -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, content)
                        }, getString(R.string.mobiletina_share_server)))
                    }
                }
            }.show()
        }
    }

    override fun onRefresh() {
        ownerActivity.importConfigViaSub()
        binding.refreshLayout.isRefreshing = false
    }

    fun scrollToSelectedServer() {
        val selectedGuid = MmkvManager.getSelectServer()
        if (selectedGuid.isNullOrEmpty()) {
            ownerActivity.toast(R.string.title_file_chooser)
            return
        }
        val position = mainViewModel.serversCache.indexOfFirst { it.guid == selectedGuid }
        val layoutManager = binding.recyclerView.layoutManager as? GridLayoutManager
        if (position >= 0 && layoutManager != null) {
            binding.recyclerView.post {
                layoutManager.scrollToPositionWithOffset(position, binding.recyclerView.height / 3)
            }
        }
    }
}
