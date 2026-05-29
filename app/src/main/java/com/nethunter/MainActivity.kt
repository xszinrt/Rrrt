package com.nethunter

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.nethunter.databinding.ActivityMainBinding
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: DomainAdapter
    private var domainList = mutableListOf<String>()
    private var filteringJob: Job? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        loadAndFilterDomains(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupRecyclerView()
        setupClickListeners()
        requestPermissions()
    }

    private fun setupRecyclerView() {
        adapter = DomainAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnImport.setOnClickListener { filePicker.launch(arrayOf("text/plain", "text/csv")) }
        binding.btnDelete.setOnClickListener { clearAll() }
        binding.btnStop.setOnClickListener { stopScan() }
        binding.btnPause.setOnClickListener { pauseScan() }
        binding.btnResume.setOnClickListener { resumeScan() }
        binding.btnExport.setOnClickListener { exportResults() }
        binding.btnCopyAll.setOnClickListener { copyAllDomains() }
        binding.etSearch.addTextChangedListener { android.text.TextWatcher { afterTextChanged { s -> adapter.filter(s.toString()) } } }
    }

    private fun loadAndFilterDomains(uri: Uri) {
        binding.progressFilter.visibility = View.VISIBLE
        binding.tvFilterStatus.visibility = View.VISIBLE
        binding.btnImport.isEnabled = false

        filteringJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val allDomains = mutableListOf<String>()
                val comDomains = mutableListOf<String>()
                
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    reader.forEachLine { line ->
                        val trimmed = line.trim().lowercase()
                        allDomains.add(trimmed)
                    }
                }
                
                val totalLines = allDomains.size
                
                for ((index, domain) in allDomains.withIndex()) {
                    if (domain.endsWith(".com")) {
                        comDomains.add(domain)
                    }
                    if (index % 100 == 0 || index == totalLines - 1) {
                        val progress = ((index + 1) * 100 / totalLines)
                        withContext(Dispatchers.Main) {
                            binding.progressFilter.progress = progress
                            binding.tvFilterStatus.text = "جاري التصفية: ${index + 1} / $totalLines ($progress%)"
                            binding.tvStats.text = "إجمالي الملف: $totalLines سطر | نطاقات .com: ${comDomains.size}"
                        }
                    }
                }
                
                domainList = comDomains
                
                withContext(Dispatchers.Main) {
                    binding.progressFilter.progress = 100
                    binding.tvFilterStatus.text = "✅ تم الاستيراد: ${comDomains.size} نطاق .com"
                    binding.btnImport.isEnabled = true
                    delay(1500)
                    binding.progressFilter.visibility = View.GONE
                    binding.tvFilterStatus.visibility = View.GONE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.progressFilter.visibility = View.GONE
                    binding.tvFilterStatus.visibility = View.GONE
                    binding.btnImport.isEnabled = true
                }
            }
        }
    }

    private fun startScan() {
        if (domainList.isEmpty()) {
            Toast.makeText(this, "لا توجد نطاقات .com للفحص!", Toast.LENGTH_SHORT).show()
            return
        }
        val delay = binding.etDelay.text.toString().toLongOrNull() ?: 500L
        val timeout = binding.etTimeout.text.toString().toLongOrNull() ?: 5000L
        val intent = Intent(this, ScanService::class.java).apply {
            putStringArrayListExtra("domains", ArrayList(domainList))
            putExtra("delay", delay)
            putExtra("timeout", timeout)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
        binding.btnStop.isEnabled = true
        binding.btnPause.isEnabled = true
        binding.btnResume.isEnabled = true
        startMonitoring()
    }

    private fun stopScan() {
        startService(Intent(this, ScanService::class.java).apply { action = ScanService.ACTION_STOP })
        resetButtons()
    }

    private fun pauseScan() {
        startService(Intent(this, ScanService::class.java).apply { action = ScanService.ACTION_PAUSE })
    }

    private fun resumeScan() {
        startService(Intent(this, ScanService::class.java).apply { action = ScanService.ACTION_RESUME })
    }

    private fun resetButtons() {
        binding.btnStop.isEnabled = false
        binding.btnPause.isEnabled = false
        binding.btnResume.isEnabled = false
    }

    private fun startMonitoring() {
        CoroutineScope(Dispatchers.Main).launch {
            while (ScanService.isRunning) {
                binding.tvProgressText.text = "${ScanService.progress} / ${ScanService.total}"
                binding.progressBar.progress = ScanService.progress
                binding.progressBar.max = ScanService.total
                binding.tvRegistered.text = ScanService.registered.toString()
                binding.tvFailed.text = ScanService.failed.toString()
                binding.tvTotal.text = ScanService.total.toString()
                binding.tvResultCount.text = "${ScanService.registered} نطاق محجوز"
                adapter.submitList(ScanService.results.toList())
                delay(500)
            }
            resetButtons()
        }
    }

    private fun exportResults() {
        // تنفيذ التصدير لاحقاً (اختياري)
        Toast.makeText(this, "سيتم إضافة التصدير قريباً", Toast.LENGTH_SHORT).show()
    }

    private fun copyAllDomains() {
        val domains = adapter.currentList.map { it.name }.joinToString("\n")
        if (domains.isNotEmpty()) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("domains", domains))
            Toast.makeText(this, "تم نسخ ${adapter.currentList.size} نطاق", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "لا توجد نتائج لنسخها", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearAll() {
        domainList.clear()
        ScanService.results.clear()
        ScanService.registered = 0
        ScanService.failed = 0
        ScanService.progress = 0
        adapter.submitList(emptyList())
        binding.tvFileName.text = "لم يتم اختيار ملف"
        binding.tvStats.text = "إجمالي الملف: 0 سطر | نطاقات .com: 0"
        binding.tvProgressText.text = "0 / 0"
        binding.progressBar.progress = 0
        binding.tvRegistered.text = "0"
        binding.tvFailed.text = "0"
        binding.tvTotal.text = "0"
        binding.tvResultCount.text = "0 نطاق محجوز"
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        filteringJob?.cancel()
    }
}
