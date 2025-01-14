package com.vanced.manager.ui.fragments

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import com.vanced.manager.R
import com.vanced.manager.core.ui.base.BindingFragment
import com.vanced.manager.databinding.FragmentLogBinding
import com.vanced.manager.utils.AppUtils
import com.vanced.manager.utils.AppUtils.logs
import com.vanced.manager.utils.managerFilepath
import com.vanced.manager.utils.performStorageAction
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.*

class LogFragment : BindingFragment<FragmentLogBinding>() {

    override fun binding(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = FragmentLogBinding.inflate(inflater, container, false)

    override fun otherSetups() {
        binding.bindData()
    }

    private fun FragmentLogBinding.bindData() {
        val logs = TextUtils.concat(*logs.toTypedArray())
        logText.text = logs
        logSave.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val second = calendar.get(Calendar.SECOND)
            try {
                performStorageAction(requireActivity()) {
                    val logPath = File("logs".managerFilepath).apply {
                        if (!exists()) {
                            mkdirs()
                        }
                    }.path
                    FileWriter(File(logPath, "$year$month${day}_$hour$minute$second.log")).apply {
                        append(logs)
                        flush()
                        close()
                    }
                }
                Toast.makeText(requireActivity(), R.string.logs_saved, Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Toast.makeText(requireActivity(), R.string.logs_not_saved, Toast.LENGTH_SHORT).show()
                AppUtils.log("VMIO", "Could not save logs: $e")
            }
        }
    }

}