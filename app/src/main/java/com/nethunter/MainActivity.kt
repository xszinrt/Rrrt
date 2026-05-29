package com.nethunter

import android.Manifest
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
        binding.btnStart.setOnClickListener { startScan() }
        binding.btnStop.setOnClickListener { stopScan() }
    }

    private fun loadAndFilterDomains(uri: Uri) {
        // إظهار شريط التقدم
        binding.progressFilter.visibility = View.VISIBLE
        binding.tvFilterStatus.visibility = View.VISIBLE
        binding.btnStart.isEnabled = false
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
                    // الاحتفاظ فقط بنطاقات .com
                    if (domain.endsWith(".com")) {
                        comDomains.add(domain)
                    }
                    
                    if (index % 100 == 0 || index == totalLines - 1) {
                        val progress = ((index + 1) * 100 / totalLines)
                        withContext(Dispatchers.Main) {
                            binding.progressFilter.progress = progress
                            binding.tvFilterStatus.text = "⏳ جاري التصفية: ${index + 1} / $totalLines ($progress%) - وجدنا ${comDomains.size} نطاق .com"
                        }
                    }
                }
                
                domainList = comDomains
                
                withContext(Dispatchers.Main) {
                    binding.progressFilter.progress = 100
                    binding.tvFilterStatus.text = "✅ تم الاستيراد: ${comDomains.size} نطاق .com من أصل $totalLines سطر"
                    binding.tvFileName.text = "📄 ${comDomains.size} نطاق .com"
                    binding.btnStart.isEnabled = comDomains.isNotEmpty()
                    binding.btnImport.isEnabled = true
                    
                    if (comDomains.isEmpty()) {
                        Toast.makeText(this@MainActivity, "لم يتم العثور على نطاقات .com في الملف!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@MainActivity, "تم استيراد ${comDomains.size} نطاق .com", Toast.LENGTH_SHORT).show()
                    }
                    
                    delay(3000)
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
        
        val intent = Intent(this, ScanService::class.java).apply {
            putStringArrayListExtra("domains", ArrayList(domainList))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        binding.btnStart.isEnabled = false
        binding.btnStop.isEnabled = true
        startMonitoring()
    }

    private fun stopScan() {
        startService(Intent(this, ScanService::class.java).apply { action = ScanService.ACTION_STOP })
        binding.btnStart.isEnabled = true
        binding.btnStop.isEnabled = false
    }

    private fun startMonitoring() {
        CoroutineScope(Dispatchers.Main).launch {
            while (ScanService.isRunning) {
                binding.tvProgress.text = "${ScanService.progress} / ${ScanService.total}"
                binding.progressBar.progress = ScanService.progress
                binding.progressBar.max = ScanService.total
                binding.tvRegistered.text = "✅ ${ScanService.registered}"
                binding.tvFailed.text = "❌ ${ScanService.failed}"
                adapter.submitList(ScanService.results.toList())
                delay(500)
            }
            binding.btnStart.isEnabled = true
            binding.btnStop.isEnabled = false
        }
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
