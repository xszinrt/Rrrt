package com.nethunter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        loadDomainsFromUri(uri)
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
        binding.btnImport.setOnClickListener { filePicker.launch(arrayOf("text/plain")) }
        binding.btnStart.setOnClickListener { startScan() }
        binding.btnStop.setOnClickListener { stopScan() }
    }

    private fun loadDomainsFromUri(uri: Uri) {
        try {
            val domains = mutableListOf<String>()
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.forEachLine { line ->
                    val trimmed = line.trim().lowercase()
                    if (trimmed.endsWith(".net")) domains.add(trimmed)
                }
            }
            domainList = domains
            binding.tvFileName.text = "✅ ${domains.size} .net"
            binding.btnStart.isEnabled = domains.isNotEmpty()
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startScan() {
        if (domainList.isEmpty()) return
        val intent = Intent(this, ScanService::class.java).apply {
            putStringArrayListExtra("domains", ArrayList(domainList))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
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
}
