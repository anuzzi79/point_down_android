package com.pointdown.app.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.pointdown.app.R
import com.pointdown.app.data.IssueItem
import java.util.Locale
import kotlin.math.round

class IssueAdapter(
    private val items: MutableList<IssueItem>,
    private val onDirtyChanged: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ISSUE = 0
        private const val TYPE_DIVIDER = 1
        private const val DIVIDER_KEY = "---divider---"
    }

    /** ViewHolder per le card normali */
    inner class IssueHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val keyText: TextView = itemView.findViewById(R.id.keyText)
        val summaryText: TextView = itemView.findViewById(R.id.summaryText)
        val spEdit: EditText = itemView.findViewById(R.id.spEdit)
        val downBtn: Button = itemView.findViewById(R.id.downBtn)
        val upBtn: Button = itemView.findViewById(R.id.upBtn)
        val dirtyText: TextView = itemView.findViewById(R.id.dirtyText)
        val statusLabel: TextView = itemView.findViewById(R.id.statusLabel) // ✅ nuova label
    }

    /** ViewHolder per il divisore-titolo */
    inner class DividerHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.dividerTitle)
    }

    override fun getItemViewType(position: Int): Int {
        val it = items[position]
        return if (it.key == DIVIDER_KEY) TYPE_DIVIDER else TYPE_ISSUE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_DIVIDER) {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_divider, parent, false)
            DividerHolder(v)
        } else {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_issue, parent, false)
            IssueHolder(v)
        }
    }

    private fun colorForStatus(name: String?, holder: IssueHolder): Int {
        if (name.isNullOrBlank()) return Color.parseColor("#BDBDBD") // grigio chiaro default
        val s = name.trim().lowercase(Locale.getDefault())
        return when {
            s == "done" -> Color.parseColor("#2E7D32") // verde
            s == "in progress" -> ContextCompat.getColor(holder.itemView.context, android.R.color.holo_blue_light) // azzurro
            s.contains("need rec") || s.contains("need req") -> Color.parseColor("#FBC02D") // giallo
            s == "blocked" -> Color.parseColor("#D32F2F") // rosso
            s == "to do" -> Color.parseColor("#BDBDBD") // grigio chiaro
            else -> Color.parseColor("#BDBDBD")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val currentItem = items[position]
        if (holder is DividerHolder) {
            // Semplice titolo di sezione (nessuna interazione, nessun bottone)
            holder.title.text = currentItem.summary ?: ""
            return
        }

        holder as IssueHolder
        Log.e("IssueAdapter", "🔗 Binding item ${currentItem.key} - ${currentItem.summary}")

        holder.keyText.text = currentItem.key
        holder.summaryText.text = currentItem.summary ?: "(sem resumo)"
        holder.spEdit.setText(currentItem.newSp.toString())

        // ✅ Stato: testo + colore
        val st = currentItem.statusName
        if (st.isNullOrBlank()) {
            holder.statusLabel.visibility = View.GONE
        } else {
            holder.statusLabel.visibility = View.VISIBLE
            holder.statusLabel.text = st
            holder.statusLabel.setTextColor(colorForStatus(st, holder))
        }

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

        // 👇 Click sul nome della card apre Jira
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
