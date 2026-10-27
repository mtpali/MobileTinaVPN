package com.v2ray.ang.ui

import android.graphics.Color
import android.os.SystemClock
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.v2ray.ang.contracts.BaseAdapterListener
import com.v2ray.ang.databinding.ItemRecyclerSubSettingBinding
import com.v2ray.ang.helper.ItemTouchHelperAdapter
import com.v2ray.ang.helper.ItemTouchHelperViewHolder
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.SubscriptionsViewModel

class SubSettingRecyclerAdapter(
    private val viewModel: SubscriptionsViewModel,
    private val adapterListener: BaseAdapterListener?,
    private val secretMode: Boolean = false,
    private val onSecretTap: (String) -> Unit = {}
) : RecyclerView.Adapter<SubSettingRecyclerAdapter.MainViewHolder>(), ItemTouchHelperAdapter {

    override fun getItemCount() = viewModel.getAll().size

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        val subscriptions = viewModel.getAll()
        val subId = subscriptions[position].guid
        val subItem = subscriptions[position].subscription
        holder.itemSubSettingBinding.tvName.text = subItem.remarks
        val allowed = com.v2ray.ang.handler.ProfileActionPolicy.allows(subItem)
        holder.itemSubSettingBinding.tvUrl.text = if (allowed) subItem.url else ""
        holder.itemSubSettingBinding.layoutShare.setOnClickListener(null)
        holder.itemSubSettingBinding.layoutEdit.visibility = View.VISIBLE
        holder.itemSubSettingBinding.layoutRemove.visibility = View.VISIBLE
        holder.itemSubSettingBinding.chkEnable.isChecked = subItem.enabled
        holder.itemSubSettingBinding.tvLastUpdated.text = Utils.formatTimestamp(subItem.lastUpdated)
        holder.itemView.setBackgroundColor(Color.TRANSPARENT)
        holder.hold?.dispose()
        holder.hold = null

        holder.itemSubSettingBinding.infoContainer.setOnClickListener(null)
        holder.itemSubSettingBinding.infoContainer.setOnTouchListener(null)
        if (secretMode && com.v2ray.ang.handler.ProfileActionPolicy.allows(subItem)) {
            (holder.itemView.findViewTreeLifecycleOwner() ?: holder.itemView.context as? androidx.lifecycle.LifecycleOwner)?.let { owner ->
                holder.hold = SubscriptionHeaderHold(holder.itemSubSettingBinding.infoContainer, owner,
                    valid = { com.v2ray.ang.handler.ProfileActionPolicy.allowsSubscription(subId) },
                    onHold = { onSecretTap(subId) })
                holder.itemSubSettingBinding.infoContainer.isClickable = true
                holder.itemSubSettingBinding.infoContainer.setOnTouchListener(holder.hold)
            }
        }

        holder.itemSubSettingBinding.layoutEdit.setOnClickListener {
            adapterListener?.onEdit(subId, position)
        }

        holder.itemSubSettingBinding.layoutRemove.setOnClickListener {
            adapterListener?.onRemove(subId, position)
        }

        holder.itemSubSettingBinding.chkEnable.setOnCheckedChangeListener { it, isChecked ->
            if (!it.isPressed) return@setOnCheckedChangeListener
            subItem.enabled = isChecked
            viewModel.update(subId, subItem)
        }

        if (secretMode || !com.v2ray.ang.handler.ProfileActionPolicy.allows(subItem)) {
            holder.itemSubSettingBinding.layoutUrl.visibility = View.GONE
            holder.itemSubSettingBinding.layoutShare.visibility = View.GONE
            holder.itemSubSettingBinding.layoutEdit.visibility = View.GONE
            holder.itemSubSettingBinding.layoutRemove.visibility = View.GONE
            holder.itemSubSettingBinding.chkEnable.visibility = View.GONE
            holder.itemSubSettingBinding.layoutLastUpdated.visibility = View.VISIBLE
        } else if (TextUtils.isEmpty(subItem.url)) {
            holder.itemSubSettingBinding.layoutUrl.visibility = View.GONE
            holder.itemSubSettingBinding.layoutShare.visibility = View.INVISIBLE
            holder.itemSubSettingBinding.chkEnable.visibility = View.INVISIBLE
            holder.itemSubSettingBinding.layoutLastUpdated.visibility = View.INVISIBLE
        } else {
            holder.itemSubSettingBinding.layoutUrl.visibility = View.VISIBLE
            holder.itemSubSettingBinding.layoutShare.visibility = View.VISIBLE
            holder.itemSubSettingBinding.chkEnable.visibility = View.VISIBLE
            holder.itemSubSettingBinding.layoutLastUpdated.visibility = View.VISIBLE
            holder.itemSubSettingBinding.layoutShare.setOnClickListener {
                adapterListener?.onShare(subItem.url)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        return MainViewHolder(
            ItemRecyclerSubSettingBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onViewRecycled(holder: MainViewHolder) {
        holder.hold?.dispose()
        holder.hold = null
        super.onViewRecycled(holder)
    }

    class MainViewHolder(val itemSubSettingBinding: ItemRecyclerSubSettingBinding) :
        BaseViewHolder(itemSubSettingBinding.root), ItemTouchHelperViewHolder {
        internal var hold: SubscriptionHeaderHold? = null
    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun onItemSelected() {
            itemView.setBackgroundColor(Color.LTGRAY)
        }

        fun onItemClear() {
            itemView.setBackgroundColor(0)
        }
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        viewModel.swap(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onItemMoveCompleted() {
        adapterListener?.onRefreshData()
    }

    override fun onItemDismiss(position: Int) {
    }

}
