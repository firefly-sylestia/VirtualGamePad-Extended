package com.fireflysylestia.virtualgamepadextended.network

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import com.fireflysylestia.virtualgamepadextended.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.LinkedBlockingQueue

class ConnectionViewModel(private val onConnectionSuccess: suspend (String, Int) -> Unit = { _, _ -> }) :
    ViewModel() {

    private val tag = this::class.java.simpleName
    private var clientSocket: Socket? = null // Internal storage for the socket

    /** The endpoint of the last successful connect, used to reconnect after a drop. */
    private var lastEndpoint: NetworkCommand.Connect? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 3

    private companion object {
        /** How many times one connect request is tried before giving up. */
        const val connectAttempts = 4

        /** Per-attempt connect timeout, in milliseconds. */
        const val connectTimeoutMillis = 3000

        /** Backoff between attempts, in milliseconds. */
        val backoffMillis = longArrayOf(300L, 800L, 1500L)
    }

    // Replace gamepad state queue with a general command queue
    private val commandQueue = LinkedBlockingQueue<NetworkCommand>()

    // Job for the command processor
    private var commandProcessorJob: Job? = null

    // Expose screen UI state
    private val _uiState = MutableStateFlow(ConnectionState())
    val uiState: StateFlow<ConnectionState> = _uiState.asStateFlow()

    /**
     * Enqueues a network command for processing.
     * This is the core method for all network operations.
     *
     * @param command The network command to enqueue
     */
    private fun enqueueCommand(command: NetworkCommand) {
        // Don't enqueue send commands if disconnected or disconnecting
        // But always allow Connect and Disconnect commands
        if (command !is NetworkCommand.Connect &&
            command !is NetworkCommand.Disconnect &&
            (_uiState.value.error != null || !_uiState.value.connected)
        ) {
            Log.d(tag, "Skipping command enqueue while disconnected: $command")
            return
        }

        commandQueue.offer(command)

        // Start the command processor if not already running
        if (commandProcessorJob == null || commandProcessorJob?.isActive != true) {
            startCommandProcessor()
        }
    }

    /**
     * Enqueues a gamepad state for sending.
     * This is the public API for sending gamepad updates.
     *
     * @param gamepadState The gamepad state to enqueue
     */
    fun enqueueGamepadState(gamepadState: GamepadReading) {
        enqueueCommand(NetworkCommand.SendGamepadState(gamepadState))
    }

    /**
     * Enqueues a string to send.
     * This is primarily for testing purposes.
     *
     * @param string The string to send
     */
    @Suppress("unused")
    fun enqueueString(string: String) {
        enqueueCommand(NetworkCommand.SendString(string))
    }

    /**
     * Connect to a server at the given IP address and port.
     * This now enqueues a connection command.
     */
    fun connect(ipAddress: String, port: Int) {
        _uiState.update {
            it.copy(
                isConnecting = true,
                ipAddress = ipAddress,
                port = port,
                error = null,
                connected = false
            )
        }

        enqueueCommand(NetworkCommand.Connect(ipAddress, port))
    }

    /**
     * Disconnect from the server.
     * This now enqueues a disconnect command.
     */
    fun disconnect() {
        enqueueCommand(NetworkCommand.Disconnect)
    }

    /**
     * Starts the command processor job that executes network commands.
     * Uses a blocking queue implementation to efficiently wait for new commands.
     */
    private fun startCommandProcessor() {
        // Cancel existing job if any
        commandProcessorJob?.cancel()

        // Start new processor job
        commandProcessorJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    // This will block until a command is available
                    // Process based on command type
                    when (val command = commandQueue.take()) {
                        is NetworkCommand.Connect -> processConnectCommand(command)
                        is NetworkCommand.SendGamepadState -> processSendGamepadStateCommand(command)
                        is NetworkCommand.SendString -> processSendStringCommand(command)
                        is NetworkCommand.Disconnect -> {
                            processDisconnectCommand()
                            // Exit the loop after disconnect
                            break
                        }
                    }
                } catch (e: InterruptedException) {
                    // The coroutine was canceled, just exit the loop
                    Log.d(tag, "Command processor interrupted: ${e.message}")
                    break
                } catch (e: CancellationException) {
                    // The coroutine was canceled, just exit the loop
                    Log.d(tag, "Command processor canceled")
                    break
                } catch (e: Exception) {
                    // Catch any other exceptions to prevent the loop from crashing
                    Log.e(tag, "Command processor error: ${e.message}", e)
                    _uiState.update {
                        it.copy(
                            error = "Unexpected error: ${e.message ?: "Unknown"}",
                            connected = false
                        )
                    }

                    // Force disconnect on fatal errors
                    try {
                        processDisconnectCommand()
                    } catch (e2: Exception) {
                        Log.e(tag, "Error during emergency disconnect: ${e2.message}")
                    }
                    break
                }
            }

            Log.d(tag, "Command processor stopped")
        }
    }

    /**
     * Process a connect command.
     */
    private suspend fun processConnectCommand(command: NetworkCommand.Connect) {
        var lastError: String? = null

        // A single attempt used to mean one dropped SYN (the PC accepting with a
        // zero backlog, a firewall delay, or a phone radio waking up) failed the
        // whole connection. Retry a few times with a short backoff instead.
        for (attempt in 0 until connectAttempts) {
            if (attempt > 0) {
                val wait = backoffMillis[attempt % backoffMillis.size]
                Log.d(tag, "Retrying connection to ${command.ipAddress}:${command.port} in ${wait}ms")
                delay(wait)
            }

            var socket: Socket? = null
            try {
                socket = Socket()
                socket.tcpNoDelay = true
                socket.setPerformancePreferences(1, 2, 0)
                socket.trafficClass = 0x10 // IPTOS_LOWDELAY

                socket.connect(
                    java.net.InetSocketAddress(command.ipAddress, command.port),
                    connectTimeoutMillis
                )
                clientSocket = socket // Store the connected socket

                _uiState.update {
                    it.copy(
                        connected = true,
                        isConnecting = false,
                        error = null
                    )
                }
                Log.d(tag, "Connected: $clientSocket (attempt ${attempt + 1})")

                lastEndpoint = command
                reconnectAttempts = 0

                // Invoke callback on successful connection
                viewModelScope.launch {
                    try {
                        onConnectionSuccess(command.ipAddress, command.port)
                    } catch (e: Exception) {
                        Log.e(tag, "Error saving connection credentials: ${e.message}", e)
                    }
                }
                return
            } catch (e: IOException) {
                lastError = e.message ?: "Unknown connection error"
                Log.w(tag, "Connect attempt ${attempt + 1}/$connectAttempts failed: $lastError")
                try {
                    socket?.close()
                } catch (closeError: IOException) {
                    Log.d(tag, "Error closing failed socket: ${closeError.message}")
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Unknown error"
                Log.e(tag, "Connect error on attempt ${attempt + 1}: ${e.message}", e)
                try {
                    socket?.close()
                } catch (closeError: IOException) {
                    Log.d(tag, "Error closing failed socket: ${closeError.message}")
                }
            }
        }

        _uiState.update {
            it.copy(
                connected = false,
                isConnecting = false,
                error = lastError ?: "Unknown connection error"
            )
        }
    }

    /**
     * Process a send gamepad state command.
     */
    private fun processSendGamepadStateCommand(command: NetworkCommand.SendGamepadState) {
        if (!_uiState.value.connected || clientSocket == null) {
            // Skip sending if we're not connected
            return
        }

        try {
            command.gamepadState.marshal(clientSocket!!.outputStream, null)
        } catch (e: IOException) {
            Log.e(tag, "Error sending gamepad state: ${e.message}", e)

            // Update UI with error and disconnect when socket errors occur
            _uiState.update {
                it.copy(
                    error = "Connection lost: ${e.message ?: "Unknown error"}",
                    connected = false  // Mark as disconnected immediately
                )
            }

            // A dropped socket is usually the phone radio sleeping or moving
            // out of range. Try to re-establish the last endpoint before
            // giving up, so a brief drop does not end the session.
            if (e is SocketException) {
                Log.d(tag, "Socket exception detected, attempting to reconnect")
                clientSocket?.close()
                clientSocket = null
                commandQueue.clear()

                val endpoint = lastEndpoint
                if (endpoint != null && reconnectAttempts < maxReconnectAttempts) {
                    reconnectAttempts++
                    Log.d(
                        tag,
                        "Reconnecting to ${endpoint.ipAddress}:${endpoint.port} " +
                            "(attempt $reconnectAttempts/$maxReconnectAttempts)"
                    )
                    _uiState.update {
                        it.copy(
                            error = "Connection lost, reconnecting " +
                                "($reconnectAttempts/$maxReconnectAttempts)...",
                            connected = false
                        )
                    }
                    commandQueue.offer(endpoint)
                } else {
                    // Throw exception to break out of the command processor loop
                    throw e
                }
            } else {
                // For other IO exceptions, try graceful disconnect
                commandQueue.clear()  // Clear other pending commands
                commandQueue.offer(NetworkCommand.Disconnect)  // Make sure we disconnect properly
            }
        }
    }

    /**
     * Process a send string command.
     */
    private fun processSendStringCommand(command: NetworkCommand.SendString) {
        if (!_uiState.value.connected || clientSocket == null) {
            // Skip sending if we're not connected
            return
        }

        try {
            clientSocket!!.outputStream.write(command.string.toByteArray())
        } catch (e: IOException) {
            Log.e(tag, "Error sending string: ${e.message}", e)

            // Update UI with error and disconnect when socket errors occur
            _uiState.update {
                it.copy(
                    error = "Connection lost: ${e.message ?: "Unknown error"}",
                    connected = false  // Mark as disconnected immediately
                )
            }

            // A dropped socket is usually the phone radio sleeping or moving
            // out of range. Try to re-establish the last endpoint before
            // giving up, so a brief drop does not end the session.
            if (e is SocketException) {
                Log.d(tag, "Socket exception detected, attempting to reconnect")
                clientSocket?.close()
                clientSocket = null
                commandQueue.clear()

                val endpoint = lastEndpoint
                if (endpoint != null && reconnectAttempts < maxReconnectAttempts) {
                    reconnectAttempts++
                    Log.d(
                        tag,
                        "Reconnecting to ${endpoint.ipAddress}:${endpoint.port} " +
                            "(attempt $reconnectAttempts/$maxReconnectAttempts)"
                    )
                    _uiState.update {
                        it.copy(
                            error = "Connection lost, reconnecting " +
                                "($reconnectAttempts/$maxReconnectAttempts)...",
                            connected = false
                        )
                    }
                    commandQueue.offer(endpoint)
                } else {
                    // Throw exception to break out of the command processor loop
                    throw e
                }
            } else {
                // For other IO exceptions, try graceful disconnect
                commandQueue.clear()  // Clear other pending commands
                commandQueue.offer(NetworkCommand.Disconnect)  // Make sure we disconnect properly
            }
        }
    }

    /**
     * Process a disconnect command.
     */
    private fun processDisconnectCommand() {
        try {
            clientSocket?.close()
        } catch (e: IOException) {
            Log.e(tag, "Error closing socket: ${e.message}", e)
        } finally {
            clientSocket = null

            // Clear the queue in case there are pending commands
            commandQueue.clear()

            _uiState.update {
                ConnectionState() // Reset to initial state
            }

            Log.d(tag, "Disconnected")
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Ensure disconnection when ViewModel is cleared
        if (_uiState.value.connected || clientSocket != null) {
            Log.d(tag, "ViewModel cleared, ensuring disconnection.")
            // Force immediate disconnect rather than queueing
            try {
                clientSocket?.close()
            } catch (e: IOException) {
                Log.e(tag, "Error closing socket during cleanup: ${e.message}", e)
            } finally {
                clientSocket = null
                commandQueue.clear()
                commandProcessorJob?.cancel()
                commandProcessorJob = null
            }
        }
    }

    /**
     * Runs a series of network diagnostics to troubleshoot connection issues.
     * @param context Android context to access system services
     */
    fun runDiagnostics(context: Context) {
        val currentState = _uiState.value
        val ipAddress = currentState.ipAddress
        val port = currentState.port

        if (ipAddress.isBlank() || port == -1) {
            Log.e(tag, "Cannot run diagnostics: IP or Port missing")
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRunningDiagnostics = true,
                    diagnosticResults = emptyList()
                )
            }

            val diagnostics = NetworkDiagnostics(context)
            val results = mutableListOf<NetworkDiagnostics.DiagnosticResult>()

            // Step 1: Wi-Fi/Network
            val isWifiConnected = diagnostics.checkNetworkConnectivity()
            results.add(
                NetworkDiagnostics.DiagnosticResult(
                    NetworkDiagnostics.DiagnosticStep.WIFI,
                    isWifiConnected,
                    if (isWifiConnected) context.getString(R.string.diagnostics_pass_wifi)
                    else context.getString(R.string.diagnostics_fail_wifi)
                )
            )
            _uiState.update { it.copy(diagnosticResults = results.toList()) }
            if (!isWifiConnected) {
                _uiState.update { it.copy(isRunningDiagnostics = false) }
                return@launch
            }

            // Step 2: Local IP
            val localIp = diagnostics.getLocalIpAddress()
            val hasLocalIp = localIp != null
            results.add(
                NetworkDiagnostics.DiagnosticResult(
                    NetworkDiagnostics.DiagnosticStep.IP,
                    hasLocalIp,
                    if (hasLocalIp) context.getString(R.string.diagnostics_pass_ip, localIp)
                    else context.getString(R.string.diagnostics_fail_ip)
                )
            )
            _uiState.update { it.copy(diagnosticResults = results.toList()) }
            if (!hasLocalIp) {
                _uiState.update { it.copy(isRunningDiagnostics = false) }
                return@launch
            }

            // Step 3: Subnet check
            val sameSubnet = diagnostics.isSameSubnet(localIp, ipAddress)
            results.add(
                NetworkDiagnostics.DiagnosticResult(
                    NetworkDiagnostics.DiagnosticStep.SUBNET,
                    sameSubnet,
                    if (sameSubnet) context.getString(R.string.diagnostics_pass_subnet)
                    else context.getString(R.string.diagnostics_fail_subnet, localIp, ipAddress)
                )
            )
            _uiState.update { it.copy(diagnosticResults = results.toList()) }

            // Step 4: Ping
            val canPing = diagnostics.pingHost(ipAddress)
            results.add(
                NetworkDiagnostics.DiagnosticResult(
                    NetworkDiagnostics.DiagnosticStep.PING,
                    canPing,
                    if (canPing) context.getString(R.string.diagnostics_pass_ping)
                    else context.getString(R.string.diagnostics_fail_ping)
                )
            )
            _uiState.update { it.copy(diagnosticResults = results.toList()) }

            // Step 5: Port check
            val canConnectPort = diagnostics.checkPort(ipAddress, port)
            results.add(
                NetworkDiagnostics.DiagnosticResult(
                    NetworkDiagnostics.DiagnosticStep.PORT,
                    canConnectPort,
                    if (canConnectPort) context.getString(R.string.diagnostics_pass_port, port)
                    else context.getString(R.string.diagnostics_fail_port, port)
                )
            )
            _uiState.update {
                it.copy(
                    diagnosticResults = results.toList(),
                    isRunningDiagnostics = false
                )
            }
        }
    }

    /**
     * Clears the current diagnostic results.
     */
    fun clearDiagnostics() {
        _uiState.update { it.copy(diagnosticResults = emptyList(), isRunningDiagnostics = false) }
    }
}

