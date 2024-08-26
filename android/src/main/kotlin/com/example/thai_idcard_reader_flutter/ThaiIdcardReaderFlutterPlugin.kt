package com.example.thai_idcard_reader_flutter

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.annotation.NonNull
import com.acs.smartcard.Reader
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.nio.charset.*
import java.util.*
import org.json.JSONObject

const val ACTION_USB_PERMISSION = "com.example.thai_idcard_reader_flutter.USB_PERMISSION"
const val ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED"
const val ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED"
const val ACTION_USB_GRANTED = "android.hardware.usb.action.EXTRA_PERMISSION_GRANTED"
val customAction = "com.example.thai_idcard_reader_flutter.ACTION_USB_ATTACHED"


private fun pendingPermissionIntent(context: Context) =
    PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

/** ThaiIdcardReaderFlutterPlugin */
class ThaiIdcardReaderFlutterPlugin : FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler {

  private lateinit var channel: MethodChannel

  private var usbEventChannel: EventChannel? = null

  private var readerEventChannel: EventChannel? = null
  private var eventSink: EventChannel.EventSink? = null

  private var applicationContext: Context? = null
  private var usbManager: UsbManager? = null

  // acs
  private var mReader: Reader? = null
  private var device: UsbDevice? = null

  private var readerStreamHandler: ReaderStream? = null

  private val usbReceiver: BroadcastReceiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          println("$intent")
          val action = intent.action
          val reader = mReader
          var dev: HashMap<String, Any?>?
          device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
          println("$action")
          println("$device")
          if (action == customAction) {
            if (usbManager!!.hasPermission(device)) {
              println("has permission")
              context.registerReceiver(receiver, IntentFilter(ACTION_USB_ATTACHED))
              dev = serializeDevice(device)
              reader?.open(device)
              dev["isAttached"] = true
              dev["hasPermission"] = true
              eventSink?.success(dev)
              println("${reader!!.isSupported(device)}")
              if (reader!!.isSupported(device)) {
                println("Entering if")
                readerStreamHandler?.setReader(reader)
              }
            } else {
              context.registerReceiver(receiver, IntentFilter(ACTION_USB_PERMISSION))
              println("Inside permission")
              println("$device")
              if(device?.vendorId==1839){
              usbManager?.requestPermission(device, pendingPermissionIntent(context))
              dev = serializeDevice(device)
              dev["isAttached"] = true
              dev["hasPermission"] = false
              eventSink?.success(dev)
            }
            }
          }
          if (action == ACTION_USB_ATTACHED) {
            if (usbManager!!.hasPermission(device)) {
              println("has permission")
              dev = serializeDevice(device)
              dev["isAttached"] = true
              dev["hasPermission"] = true
              eventSink?.success(dev)
            } else {
              context.registerReceiver(receiver, IntentFilter(ACTION_USB_PERMISSION))
              println("Inside permission")
              println("$device")
              if(device?.vendorId==1839){
              usbManager?.requestPermission(device, pendingPermissionIntent(context))
              dev = serializeDevice(device)
              dev["isAttached"] = true
              dev["hasPermission"] = false
              eventSink?.success(dev)
            }
            }
          } else if (action == ACTION_USB_DETACHED) {
            println("detached")

            reader?.close()
            dev = serializeDevice(device)
            dev["isAttached"] = false
            dev["hasPermission"] = false
            eventSink?.success(dev)
          } else if (action == ACTION_USB_PERMISSION) {
            println("no permission")

            if (usbManager!!.hasPermission(device)) {
              println("has permission")

              dev = serializeDevice(device)
              reader?.open(device)
              dev["isAttached"] = true
              dev["hasPermission"] = true
              eventSink?.success(dev)
              if (reader!!.isSupported(device)) {
                readerStreamHandler?.setReader(reader)
              }
            }
          }
        }
      }

  fun serializeDevice(device: UsbDevice?): HashMap<String, Any?> {
    val dev: HashMap<String, Any?> = HashMap()
    dev["identifier"] = device?.deviceName
    dev["vendorId"] = device?.vendorId
    dev["productId"] = device?.productId
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      dev["manufacturerName"] = device?.manufacturerName
      dev["productName"] = device?.productName
      dev["interfaceCount"] = device?.interfaceCount
    }
    dev["deviceId"] = device?.deviceId
    return dev
  }

  override fun onListen(arguments: Any?, eventSink: EventChannel.EventSink?) {
    println("filkterevent")
    println("$eventSink")
    var filter = IntentFilter(ACTION_USB_PERMISSION)
    filter.addAction(ACTION_USB_DETACHED)
    filter.addAction(ACTION_USB_ATTACHED)
    filter.addAction(customAction)
    applicationContext!!.registerReceiver(usbReceiver, filter)
    println("finish filtrer")
    this.eventSink = eventSink
  }

 
  override fun onCancel(arguments: Any?) {
    eventSink = null
    usbEventChannel = null
  }

  override fun onAttachedToEngine(
      @NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding
  ) {
    println("channel")
    channel =
        MethodChannel(flutterPluginBinding.binaryMessenger, "thai_idcard_reader_flutter_channel")
    channel.setMethodCallHandler(this)
    applicationContext = flutterPluginBinding.applicationContext
    usbManager = applicationContext?.getSystemService(Context.USB_SERVICE) as UsbManager
    mReader = Reader(usbManager)
    println("finish channel")

    
    println("usbevent")

    val usbEventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "usb_stream_channel")
    usbEventChannel?.setStreamHandler(this)
    println("finiosh usbevent")

    println("readerevent")
    val readerEventChannel =
        EventChannel(flutterPluginBinding.binaryMessenger, "reader_stream_channel")
    readerStreamHandler = ReaderStream()
    readerEventChannel?.setStreamHandler(readerStreamHandler)
    println("finish readerevent")

    println("filkterevent")
    var filter = IntentFilter(ACTION_USB_PERMISSION)
    filter.addAction(ACTION_USB_DETACHED)
    filter.addAction(ACTION_USB_ATTACHED)
    filter.addAction(customAction)
    applicationContext!!.registerReceiver(usbReceiver, filter)
    println("finish filtrer")
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    usbManager = null
    mReader = null
    applicationContext = null
    device = null
    usbEventChannel?.setStreamHandler(null)
    readerEventChannel?.setStreamHandler(null)
    readerStreamHandler = null
  }

  private val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          context.unregisterReceiver(this)
          println("connection intent")
          println("$intent")
          val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
          val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
          println("$device")
          println("${device?.vendorId}")
          if (!granted) {
            println("Permission denied: ${device?.deviceName}")
          }
        }
      }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    when (call.method) {
      "getPlatformVersion" -> {
        result.success("Android ${Build.VERSION.RELEASE}")
      }
      "readAll" -> {
        var apdu = ThaiADPU()
        val reader = mReader ?: return result.error("IllegalState", "mReader null", null)
        try {
          val res: Map<String, Any?> = apdu.readAll(reader)
          result.success(JSONObject(res).toString())
        } catch (e: Exception) {
          result.success("ERR/kt/readAll ${e.toString()}")
        }
      }
      "read" -> {
        var apdu = ThaiADPU()
        val selected = call.argument<List<String>>("selected")
        val selectedArray: Array<String> = selected!!.toTypedArray()
        val reader = mReader ?: return result.error("IllegalState", "mReader null", null)
        try {
          val res: Map<String, Any?> = apdu.readSpecific(reader, selectedArray)
          result.success(JSONObject(res).toString())
        } catch (e: Exception) {
          result.success("ERR/kt/read ${e.toString()}")
        }
      }
      "requestPermission" -> {
        val context =
            applicationContext
                ?: return result.error("IllegalState", "applicationContext null", null)
        val manager = usbManager ?: return result.error("IllegalState", "usbManager null", null)
        val identifier = call.argument<String>("identifier")
        val device = manager.deviceList[identifier]
        if(device?.vendorId==1839){
          if (!manager.hasPermission(device)) {
          context.registerReceiver(receiver, IntentFilter(ACTION_USB_PERMISSION))
          manager.requestPermission(device, pendingPermissionIntent(context))
        }
      }
        result.success(null)
      }
      "askPermission" -> {
        val context =
            applicationContext
                ?: return result.error("IllegalState", "applicationContext null", null)
        // val manager = usbManager ?: return result.error("IllegalState", "usbManager null", null)
        var manager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val identifier = call.argument<String>("identifier")
        val deviceList = manager.deviceList
        val deviceIterator = deviceList.values.iterator()
        while (deviceIterator.hasNext()) {
            val currDevice = deviceIterator.next()
            if(currDevice?.vendorId==1839){
              usbEventChannel?.setStreamHandler(this)              
               if (!manager.hasPermission(currDevice)) {
              context.registerReceiver(receiver, IntentFilter(ACTION_USB_PERMISSION))
              manager.requestPermission(currDevice, pendingPermissionIntent(context))
             }
            else{
            device= currDevice
          //Register the usbReceiver for the custom action
              context.registerReceiver(usbReceiver, IntentFilter(customAction))
          //Create an intent with the custom action
              val intent = Intent(customAction).apply {
                  putExtra(UsbManager.EXTRA_DEVICE, currDevice)
              }
          //Send the custom broadcast
              context.sendBroadcast(intent)
          }
          }
      }
        result.success(null)
      }
      else -> result.notImplemented()
    }
  }
}
