package com.pointdown.app.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.pointdown.app.R
import com.pointdown.app.data.IssueItem
import kotlin.math.round

class IssueAdapter(
    private val items: MutableList<IssueItem>,
    private val onDirtyChanged: () -> Unit
) : RecyclerView.Adapter<IssueAdapter.Holder>() {

    inner class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val keyText: TextView = itemView.findViewById(R.id.keyText)
        val summaryText: TextView = itemView.findViewById(R.id.summaryText)
        val spEdit: EditText = itemView.findViewById(R.id.spEdit)
        val downBtn: Button = itemView.findViewById(R.id.downBtn)
        val upBtn: Button = itemView.findViewById(R.id.upBtn)
        val dirtyText: TextView = itemView.findViewById(R.id.dirtyText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_issue, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val currentItem = items[position]
        Log.e("IssueAdapter", "🔗 Binding item ${currentItem.key} - ${currentItem.summary}")

        holder.keyText.text = currentItem.key
        holder.summaryText.text = currentItem.summary ?: "(sem resumo)"
        holder.spEdit.setText(currentItem.newSp.toString())

        fun setDirtyStatus(isDirty: Boolean) {
            currentItem.dirty = isDirty
            holder.dirtyText.visibility = if (isDirty) View.VISIBLE else View.GONE
            onDirtyChanged()
        }

        fun clampToHalfStep(value: Double): Double {
            val roundedValue = (round(value * 2.0) / 2.0)
            return if (roundedValue < 0.0) 0.0 else roundedValue
        }

        holder.downBtn.setOnClickListener {
            val currentValue = holder.spEdit.text.toString().toDoubleOrNull() ?: currentItem.newSp
            val newValue = clampToHalfStep(currentValue - 0.5)
            currentItem.newSp = newValue
            holder.spEdit.setText(newValue.toString())
            setDirtyStatus(true)
        }

        holder.upBtn.setOnClickListener {
            val currentValue = holder.spEdit.text.toString().toDoubleOrNull() ?: currentItem.newSp
            val newValue = clampToHalfStep(currentValue + 0.5)
            currentItem.newSp = newValue
            holder.spEdit.setText(newValue.toString())
            setDirtyStatus(true)
        }

        holder.spEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val enteredValue = holder.spEdit.text.toString().toDoubleOrNull()
                if (enteredValue != null) {
                    val newValue = clampToHalfStep(enteredValue)
                    if (newValue != currentItem.newSp) {
                        currentItem.newSp = newValue
                        holder.spEdit.setText(newValue.toString())
                        setDirtyStatus(true)
                    }
                }
            }
        }

        // 👇 Ora il click sul nome della card apre Jira
        holder.keyText.setOnClickListener { view ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentItem.browseUrl))
            view.context.startActivity(intent)
        }

        holder.dirtyText.visibility = if (currentItem.dirty) View.VISIBLE else View.GONE
    }

    override fun getItemCount(): Int = items.size

    fun setData(newItems: List<IssueItem>) {
        Log.e("IssueAdapter", "📥 setData chiamato con ${newItems.size} items")
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
