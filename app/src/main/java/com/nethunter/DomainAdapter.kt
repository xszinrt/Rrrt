package com.nethunter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nethunter.databinding.ItemDomainBinding

class DomainAdapter : RecyclerView.Adapter<DomainAdapter.ViewHolder>() {
    private var items = listOf<DomainResult>()
    
    fun submitList(list: List<DomainResult>) { 
        items = list
        notifyDataSetChanged() 
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemDomainBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) { 
        holder.bind(items[position]) 
    }
    
    override fun getItemCount() = items.size
    
    class ViewHolder(private val binding: ItemDomainBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DomainResult) {
            binding.tvDomain.text = item.name
            binding.tvReg.text = "سجل: ${item.regDate ?: "?"}"
            binding.tvExp.text = "ينتهي: ${item.expDate ?: "?"}"
        }
    }
}
