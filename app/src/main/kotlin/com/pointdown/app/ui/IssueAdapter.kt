package com.pointdown.app.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
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
    // callback con stato aggregato: true se esiste almeno una card “dirty”
    private val onDirtyChanged: (Boolean) -> Unit,
    // callback per i toggle "Mostra/Oculta zerados"
    private val onToggleClick: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_ISSUE = 0
        const val TYPE_DIVIDER = 1
        const val TYPE_TOGGLE = 2

        const val DIVIDER_KEY = "---divider---"
        const val TOGGLE_MAIN = "---toggle-main---"
        const val TOGGLE_SPECIAL = "---toggle-special---"
    }

    /** ViewHolder per le card normali */
    inner class IssueHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val keyText: TextView = itemView.findViewById(R.id.keyText)
        val summaryText: TextView = itemView.findViewById(R.id.summaryText)
        val spEdit: EditText = itemView.findViewById(R.id.spEdit)
        val downBtn: Button = itemView.findViewById(R.id.downBtn)
        val upBtn: Button = itemView.findViewById(R.id.upBtn)
        val dirtyText: TextView = itemView.findViewById(R.id.dirtyText)
        val statusLabel: TextView = itemView.findViewById(R.id.statusLabel)
    }

    /** ViewHolder per il divisore-titolo */
    inner class DividerHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.dividerTitle)
    }

    /** ViewHolder per il toggle Mostra/Oculta */
    inner class ToggleHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.toggleText)
    }

    override fun getItemViewType(position: Int): Int {
        val it = items[position]
        return when (it.key) {
            DIVIDER_KEY -> TYPE_DIVIDER
            TOGGLE_MAIN, TOGGLE_SPECIAL -> TYPE_TOGGLE
            else -> TYPE_ISSUE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_DIVIDER -> {
                val v = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_divider, parent, false)
                DividerHolder(v)
            }
            TYPE_TOGGLE -> {
                val v = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_toggle, parent, false)
                ToggleHolder(v)
            }
            else -> {
                val v = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_issue, parent, false)
                IssueHolder(v)
            }
        }
    }

    private fun colorForStatus(name: String?, holder: IssueHolder): Int {
        if (name.isNullOrBlank()) return Color.parseColor("#BDBDBD")
        val s = name.trim().lowercase(Locale.getDefault())
        return when {
            s == "done" -> Color.parseColor("#2E7D32")
            s == "in progress" -> ContextCompat.getColor(holder.itemView.context, android.R.color.holo_blue_light)
            s.contains("need rec") || s.contains("need req") -> Color.parseColor("#FBC02D")
            s == "blocked" -> Color.parseColor("#D32F2F")
            s == "to do" -> Color.parseColor("#BDBDBD")
            else -> Color.parseColor("#BDBDBD")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val currentItem = items[position]

        when (holder) {
            is DividerHolder -> {
                holder.title.text = currentItem.summary ?: ""
                return
            }

            is ToggleHolder -> {
                holder.text.text = currentItem.summary ?: ""
                holder.text.setOnClickListener {
                    when (currentItem.key) {
                        TOGGLE_MAIN -> onToggleClick(TOGGLE_MAIN)
                        TOGGLE_SPECIAL -> onToggleClick(TOGGLE_SPECIAL)
                    }
                }
                return
            }

            is IssueHolder -> {
                holder.keyText.text = currentItem.key
                holder.summaryText.text = currentItem.summary ?: "(sem resumo)"
                holder.spEdit.setText(currentItem.newSp.toString())

                // Stato + colore
                val st = currentItem.statusName
                if (st.isNullOrBlank()) {
                    holder.statusLabel.visibility = View.GONE
                } else {
                    holder.statusLabel.visibility = View.VISIBLE
                    holder.statusLabel.text = st
                    holder.statusLabel.setTextColor(colorForStatus(st, holder))
                }

                fun updateDirtyUiAndNotify() {
                    val actuallyDirty = currentItem.newSp != currentItem.sp
                    currentItem.dirty = actuallyDirty
                    holder.dirtyText.visibility = if (actuallyDirty) View.VISIBLE else View.GONE
                    val anyDirty = items.any {
                        it.key != DIVIDER_KEY &&
                                it.key != TOGGLE_MAIN &&
                                it.key != TOGGLE_SPECIAL &&
                                it.newSp != it.sp && it.dirty
                    }
                    onDirtyChanged(anyDirty)
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
                    updateDirtyUiAndNotify()
                }

                holder.upBtn.setOnClickListener {
                    val currentValue = holder.spEdit.text.toString().toDoubleOrNull() ?: currentItem.newSp
                    val newValue = clampToHalfStep(currentValue + 0.5)
                    currentItem.newSp = newValue
                    holder.spEdit.setText(newValue.toString())
                    updateDirtyUiAndNotify()
                }

                holder.spEdit.setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        val enteredValue = holder.spEdit.text.toString().toDoubleOrNull()
                        if (enteredValue != null) {
                            val newValue = clampToHalfStep(enteredValue)
                            if (newValue != currentItem.newSp) {
                                currentItem.newSp = newValue
                                holder.spEdit.setText(newValue.toString())
                            }
                            updateDirtyUiAndNotify()
                        }
                    }
                }

                holder.keyText.setOnClickListener { view ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentItem.browseUrl))
                    view.context.startActivity(intent)
                }

                holder.dirtyText.visibility =
                    if (currentItem.newSp != currentItem.sp && currentItem.dirty) View.VISIBLE else View.GONE
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun setData(newItems: List<IssueItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
        onDirtyChanged(false)
    }
}
