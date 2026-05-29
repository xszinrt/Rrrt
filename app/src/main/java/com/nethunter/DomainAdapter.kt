package com.nethunter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.nethunter.databinding.ItemDomainBinding

class DomainAdapter : RecyclerView.Adapter<DomainAdapter.ViewHolder>() {
    private var items = listOf<DomainResult>()
    private var allItems = listOf<DomainResult>()

    fun submitList(list: List<DomainResult>) {
        allItems = list
        items = list
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        items = if (query.isEmpty()) {
            allItems
        } else {
            allItems.filter { it.name.contains(query, ignoreCase = true) }
        }
        notifyDataSetChanged()
    }

    fun getCurrentList(): List<DomainResult> = items

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemDomainBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemDomainBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DomainResult) {
            binding.tvDomain.text = item.name
            binding.tvExp.text = "ينتهي: ${item.expDate ?: "غير معروف"}"
            binding.tvError.visibility = if (item.regDate == null) android.view.View.VISIBLE else android.view.View.GONE

            binding.btnGoogle.setOnClickListener {
                val url = "https://www.google.com/search?q=site:${item.name}"
                it.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            binding.btnCopy.setOnClickListener {
                val clipboard = it.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("domain", item.name))
                Toast.makeText(it.context, "تم النسخ", Toast.LENGTH_SHORT).show()
            }
            binding.btnOpen.setOnClickListener {
                val url = "https://${item.name}"
                it.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
    }
}
