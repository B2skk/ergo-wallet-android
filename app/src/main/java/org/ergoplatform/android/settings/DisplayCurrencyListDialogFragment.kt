package org.ergoplatform.android.settings

import android.os.Bundle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.ergoplatform.android.NodeConnector
import org.ergoplatform.android.R
import org.ergoplatform.android.databinding.FragmentDisplayCurrencyDialogBinding
import org.ergoplatform.android.databinding.FragmentDisplayCurrencyDialogItemBinding
import org.ergoplatform.android.saveDisplayCurrency
import java.util.*

/**
 *
 * Lists available display currencies
 */
class DisplayCurrencyListDialogFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentDisplayCurrencyDialogBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentDisplayCurrencyDialogBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val nodeConnector = NodeConnector.getInstance()
        nodeConnector.fetchCurrencies()

        binding.list.layoutManager =
            LinearLayoutManager(context)

        nodeConnector.currencies.observe(viewLifecycleOwner, {
            if (it != null && it.isNotEmpty()) {
                val listWithNone = arrayListOf<String>()
                listWithNone.add("")
                listWithNone.addAll(it.sorted())

                binding.list.adapter = DisplayCurrencyAdapter(listWithNone)
                binding.list.visibility = View.VISIBLE
            }

            binding.progressCircular.visibility = if (it == null) View.VISIBLE else View.GONE
            binding.connectionError.visibility =
                if (it != null && it.isEmpty()) View.VISIBLE else View.GONE
            binding.list.visibility = if (it != null && it.isNotEmpty()) View.VISIBLE else View.GONE

        })
    }

    private fun onChooseCurrency(currency: String) {
        saveDisplayCurrency(requireContext(), currency)
        NodeConnector.getInstance().invalidateCache()
        (parentFragment as? SettingsFragment)?.setDisplayCurrency()
        dismiss()
    }

    private inner class ViewHolder internal constructor(binding: FragmentDisplayCurrencyDialogItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        internal val text: TextView = binding.text
    }

    private inner class DisplayCurrencyAdapter internal constructor(private val items: List<String>) :
        RecyclerView.Adapter<ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

            return ViewHolder(
                FragmentDisplayCurrencyDialogItemBinding.inflate(
                    LayoutInflater.from(
                        parent.context
                    ), parent, false
                )
            )

        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val currency = items.get(position)
            holder.text.text =
                if (currency.isEmpty()) getString(R.string.label_none) else currency.toUpperCase(
                    Locale.getDefault()
                )
            holder.text.setOnClickListener { onChooseCurrency(currency) }
        }

        override fun getItemCount(): Int {
            return items.size
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}