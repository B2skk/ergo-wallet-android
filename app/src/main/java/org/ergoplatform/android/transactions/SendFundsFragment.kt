package org.ergoplatform.android.transactions

import StageConstants
import android.animation.LayoutTransition
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.core.view.descendants
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.zxing.integration.android.IntentIntegrator
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent
import org.ergoplatform.*
import org.ergoplatform.android.*
import org.ergoplatform.android.databinding.FragmentSendFundsBinding
import org.ergoplatform.android.databinding.FragmentSendFundsTokenItemBinding
import org.ergoplatform.android.ui.*
import org.ergoplatform.android.wallet.WalletConfigDbEntity
import org.ergoplatform.android.wallet.WalletTokenDbEntity
import org.ergoplatform.android.wallet.addresses.AddressChooserCallback
import org.ergoplatform.android.wallet.addresses.ChooseAddressListDialogFragment
import org.ergoplatform.android.wallet.addresses.getAddressLabel
import org.ergoplatform.android.wallet.getNumOfAddresses
import org.ergoplatform.transactions.PromptSigningResult
import kotlin.math.max


/**
 * Here's the place to send transactions
 */
class SendFundsFragment : AbstractAuthenticationFragment(), PasswordDialogCallback,
    AddressChooserCallback {
    private var _binding: FragmentSendFundsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: SendFundsViewModel
    private val args: SendFundsFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel =
            ViewModelProvider(this).get(SendFundsViewModel::class.java)

        // Inflate the layout for this fragment
        _binding = FragmentSendFundsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.initWallet(
            requireContext(),
            args.walletId,
            args.derivationIdx,
            args.paymentRequest
        )

        // Add observers
        viewModel.walletName.observe(viewLifecycleOwner, {
            // when wallet is loaded, wallet name is set. we can init everything wallet specific here
            binding.walletName.text = getString(R.string.label_send_from, it)
            binding.hintReadonly.visibility =
                if (viewModel.wallet!!.walletConfig.secretStorage == null) View.VISIBLE else View.GONE
            enableLayoutChangeAnimations()
        })
        viewModel.address.observe(viewLifecycleOwner, {
            binding.addressLabel.text = it?.getAddressLabel(requireContext()) ?: getString(
                R.string.label_all_addresses,
                viewModel.wallet?.getNumOfAddresses()
            )
        })
        viewModel.walletBalance.observe(viewLifecycleOwner, {
            binding.tvBalance.text = getString(
                R.string.label_wallet_balance,
                it.toStringRoundToDecimals(4)
            )
        })
        viewModel.feeAmount.observe(viewLifecycleOwner, {
            binding.tvFee.text = getString(
                R.string.desc_fee,
                it.toStringRoundToDecimals(4)
            )
        })
        viewModel.grossAmount.observe(viewLifecycleOwner, {
            binding.grossAmount.amount = it.toDouble()
            val nodeConnector = NodeConnector.getInstance()
            binding.tvFiat.visibility =
                if (nodeConnector.fiatCurrency.isNotEmpty()) View.VISIBLE else View.GONE
            binding.tvFiat.setText(
                getString(
                    R.string.label_fiat_amount,
                    formatFiatToString(
                        viewModel.amountToSend.toDouble() * (nodeConnector.fiatValue.value
                            ?: 0f).toDouble(),
                        nodeConnector.fiatCurrency, requireContext()
                    ),
                )
            )
        })
        viewModel.tokensChosenLiveData.observe(viewLifecycleOwner, {
            refreshTokensList()
        })
        viewModel.lockInterface.observe(viewLifecycleOwner, {
            if (it)
                ProgressBottomSheetDialogFragment.showProgressDialog(childFragmentManager)
            else
                ProgressBottomSheetDialogFragment.dismissProgressDialog(childFragmentManager)
        })
        viewModel.txWorkDoneLiveData.observe(viewLifecycleOwner, {
            if (!it.success) {
                val snackbar = Snackbar.make(
                    requireView(),
                    if (it is PromptSigningResult)
                        R.string.error_prepare_transaction
                    else R.string.error_send_transaction,
                    Snackbar.LENGTH_LONG
                )
                it.errorMsg?.let { errorMsg ->
                    snackbar.setAction(
                        R.string.label_details
                    ) {
                        MaterialAlertDialogBuilder(requireContext())
                            .setMessage(errorMsg)
                            .setPositiveButton(R.string.button_copy) { _, _ ->
                                val clipboard = ContextCompat.getSystemService(
                                    requireContext(),
                                    ClipboardManager::class.java
                                )
                                val clip = ClipData.newPlainText("", errorMsg)
                                clipboard?.setPrimaryClip(clip)
                            }
                            .setNegativeButton(R.string.label_dismiss, null)
                            .show()
                    }
                }
                snackbar.setAnchorView(R.id.nav_view).show()
            } else if (it is PromptSigningResult) {
                // if this is a prompt signing result, switch to prompt signing dialog
                SigningPromptDialogFragment().show(childFragmentManager, null)
            }
        })
        viewModel.txId.observe(viewLifecycleOwner, {
            it?.let {
                binding.cardviewTxEdit.visibility = View.GONE
                binding.cardviewTxDone.visibility = View.VISIBLE
                binding.labelTxId.text = it
            }
        })

        // Add click listeners
        binding.addressLabel.setOnClickListener {
            viewModel.wallet?.let { wallet ->
                ChooseAddressListDialogFragment.newInstance(
                    wallet.walletConfig.id, true
                ).show(childFragmentManager, null)
            }
        }
        binding.buttonShareTx.setOnClickListener {
            val txUrl =
                StageConstants.EXPLORER_WEB_ADDRESS + "en/transactions/" + binding.labelTxId.text.toString()
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, txUrl)
                type = "text/plain"
            }

            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)
        }
        binding.buttonDismiss.setOnClickListener {
            val succeeded = findNavController().popBackStack()
            // back stack might be empty when coming from a deep link
            if (!succeeded) {
                findNavController().navigate(R.id.navigation_wallet)
            }
        }

        binding.buttonSend.setOnClickListener {
            startPayment()
        }

        binding.buttonScan.setOnClickListener {
            IntentIntegrator.forSupportFragment(this).initiateScan(setOf(IntentIntegrator.QR_CODE))
        }
        binding.buttonAddToken.setOnClickListener {
            ChooseTokenListDialogFragment().show(childFragmentManager, null)
        }
        binding.amount.setEndIconOnClickListener {
            setAmountEdittext(
                ErgoAmount(
                    max(
                        0L,
                        (viewModel.walletBalance.value?.nanoErgs ?: 0L)
                                - (viewModel.feeAmount.value?.nanoErgs ?: 0L)
                    )
                )
            )
        }
        binding.hintReadonly.setOnClickListener {
            openUrlWithBrowser(requireContext(), URL_COLD_WALLET_HELP)
        }

        // Init other stuff
        binding.tvReceiver.editText?.setText(viewModel.receiverAddress)
        if (viewModel.amountToSend.nanoErgs > 0) {
            setAmountEdittext(viewModel.amountToSend)
        }

        binding.amount.editText?.addTextChangedListener(MyTextWatcher(binding.amount))
        binding.tvReceiver.editText?.addTextChangedListener(MyTextWatcher(binding.tvReceiver))

        // this triggers an automatic scroll so the amount field is visible when soft keyboard is
        // opened or when amount edittext gets focus
        binding.amount.editText?.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus)
                ensureAmountVisibleDelayed()
        }
        KeyboardVisibilityEvent.setEventListener(
            requireActivity(),
            viewLifecycleOwner,
            { keyboardOpen ->
                if (keyboardOpen && binding.amount.editText?.hasFocus() == true) {
                    ensureAmountVisibleDelayed()
                }
            })
    }

    private fun enableLayoutChangeAnimations() {
        // set layout change animations. they are not set in the xml to avoid animations for the first
        // time the layout is displayed, and enabling them is delayed due to the same reason
        Handler().postDelayed({
            _binding?.let { binding ->
                binding.container.layoutTransition = LayoutTransition()
                binding.container.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
            }
        }, 200)
    }

    private fun ensureAmountVisibleDelayed() {
        // delay 200 to make sure that smart keyboard is already open
        Handler().postDelayed(
            { _binding?.let { it.scrollView.smoothScrollTo(0, it.amount.top) } },
            200
        )
    }

    override fun onAddressChosen(addressDerivationIdx: Int?) {
        viewModel.derivedAddressIdx = addressDerivationIdx
    }

    private fun refreshTokensList() {
        val tokensAvail = viewModel.tokensAvail
        val tokensChosen = viewModel.tokensChosen

        binding.buttonAddToken.visibility =
            if (tokensAvail.size > tokensChosen.size) View.VISIBLE else View.INVISIBLE
        binding.labelTokenAmountError.visibility = View.GONE
        binding.tokensList.apply {
            this.visibility =
                if (tokensChosen.isNotEmpty()) View.VISIBLE else View.GONE
            this.removeAllViews()
            tokensChosen.forEach {
                val ergoId = it.key
                tokensAvail.filter { it.tokenId.equals(ergoId) }
                    .firstOrNull()?.let { tokenDbEntity ->
                        val itemBinding =
                            FragmentSendFundsTokenItemBinding.inflate(layoutInflater, this, true)
                        itemBinding.tvTokenName.text = tokenDbEntity.name
                        itemBinding.inputTokenAmount.inputType =
                            if (tokenDbEntity.decimals!! > 0) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                            else InputType.TYPE_CLASS_NUMBER
                        itemBinding.inputTokenAmount.addTextChangedListener(
                            TokenAmountWatcher(tokenDbEntity)
                        )
                        itemBinding.inputTokenAmount.setText(
                            tokenAmountToText(it.value.value, tokenDbEntity.decimals)
                        )
                        itemBinding.buttonTokenRemove.setOnClickListener {
                            if (itemBinding.inputTokenAmount.text.isEmpty()) {
                                viewModel.removeToken(ergoId)
                            } else {
                                itemBinding.inputTokenAmount.text = null
                            }
                        }
                        itemBinding.buttonTokenAll.setOnClickListener {
                            itemBinding.inputTokenAmount.setText(
                                tokenAmountToText(tokenDbEntity.amount!!, tokenDbEntity.decimals)
                            )
                        }
                    }
            }
        }
    }

    private fun tokenAmountToText(amount: Long, decimals: Int) =
        if (amount > 0)
            TokenAmount(amount, decimals).toString()
        else ""

    private fun setAmountEdittext(amountToSend: ErgoAmount) {
        binding.amount.editText?.setText(amountToSend.toStringTrimTrailingZeros())
    }

    private fun startPayment() {
        if (!viewModel.checkReceiverAddress()) {
            binding.tvReceiver.error = getString(R.string.error_receiver_address)
            binding.tvReceiver.editText?.requestFocus()
        } else if (!viewModel.checkAmount()) {
            binding.amount.error = getString(R.string.error_amount)
            binding.amount.editText?.requestFocus()
        } else if (!viewModel.checkTokens()) {
            binding.labelTokenAmountError.visibility = View.VISIBLE
            binding.tokensList.descendants.filter { it is EditText && it.text.isEmpty() }
                .firstOrNull()
                ?.requestFocus()
        } else {
            startAuthFlow(viewModel.wallet!!.walletConfig)
        }
    }

    override fun startAuthFlow(walletConfig: WalletConfigDbEntity) {
        if (walletConfig.secretStorage == null) {
            // we have a read only wallet here, let's go to cold wallet support mode
            viewModel.startColdWalletPayment(requireContext())
        } else {
            super.startAuthFlow(walletConfig)
        }
    }

    override fun proceedAuthFlowWithPassword(password: String): Boolean {
        return viewModel.startPaymentWithPassword(password, requireContext())
    }

    override fun showBiometricPrompt() {
        hideForcedSoftKeyboard(requireContext(), binding.amount.editText!!)
        super.showBiometricPrompt()
    }

    override fun proceedAuthFlowFromBiometrics() {
        context?.let { viewModel.startPaymentUserAuth(it) }
    }

    private fun inputChangesToViewModel() {
        viewModel.receiverAddress = binding.tvReceiver.editText?.text?.toString() ?: ""

        val amountStr = binding.amount.editText?.text.toString()
        val ergoAmount = amountStr.toErgoAmount()
        viewModel.amountToSend = ergoAmount ?: ErgoAmount.ZERO
        if (ergoAmount == null) {
            // conversion error, too many decimals or too big for long
            binding.amount.error = getString(R.string.error_amount)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            result.contents?.let {
                if (viewModel.wallet?.walletConfig?.secretStorage != null
                    && isColdSigningRequestChunk(it)
                ) {
                    findNavController().navigate(
                        SendFundsFragmentDirections
                            .actionSendFundsFragmentToColdWalletSigningFragment(
                                it,
                                viewModel.wallet!!.walletConfig.id
                            )
                    )
                } else {
                    val content = parsePaymentRequestFromQrCode(it)
                    content?.let {
                        binding.tvReceiver.editText?.setText(content.address)
                        content.amount.let { amount ->
                            if (amount.nanoErgs > 0) setAmountEdittext(
                                amount
                            )
                        }
                        viewModel.addTokensFromQr(content.tokens)
                    }
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class MyTextWatcher(private val textInputLayout: TextInputLayout) : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

        }

        override fun afterTextChanged(s: Editable?) {
            textInputLayout.error = null
            inputChangesToViewModel()
        }

    }

    inner class TokenAmountWatcher(private val token: WalletTokenDbEntity) : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

        }

        override fun afterTextChanged(s: Editable?) {
            viewModel.setTokenAmount(
                token.tokenId!!,
                s?.toString()?.toTokenAmount(token.decimals!!) ?: TokenAmount(0, token.decimals!!)
            )
            binding.labelTokenAmountError.visibility = View.GONE
        }

    }
}