package mx.tecnm.tepic.ladm_u4_practica1_autocontestadora

import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.CallLog
import android.telephony.SmsManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    var baseRemota = FirebaseFirestore.getInstance()
    var listaTelefonos = ArrayList<String>()
    val siLlamada = 1
    val permisoEnviar = 2
    var status = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listarLlamadas()
        mensajebueno.setText(R.string.mensajeA)
        mensajemalo.setText(R.string.mensajeB)

        //PERMISO ENVIAR MENSAJES
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.SEND_SMS)!= PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.SEND_SMS),permisoEnviar)
        }

        agregar.setOnClickListener {
            insertarContactos()
        }

        obtenerlista.setOnClickListener {
            listarLlamadas()
        }

        var timer = object : CountDownTimer(60000,5000){
            override fun onTick(millisUntilFinished: Long) {
                if (status){
                    listarLlamadas()
                    alerta("Buscando llamadas perdidas")
                }else{
                    mensaje("Se mandaron los mensaje a los contactos que tenian llamadas perdidas")
                    cancel()
                }
            }

            override fun onFinish() {
                envioSMS()
                start()
            }
        }.start()

    }

    private fun envioSMS() {
        var tipo = ""
        var mensajeAgradable = mensajebueno.text.toString()
        var mensajeDesagrable = mensajemalo.text.toString()
        if (listaTelefonos.isEmpty()){
            mensaje("No hay llamadas perdidas")
        }else {
            var telefonTelAviv = ""
            listaTelefonos.forEach {
                baseRemota.collection("contactos").addSnapshotListener { querySnapshot, error ->
                    if (error != null) {
                        mensaje(error.message!!)
                        return@addSnapshotListener
                    }
                    for (document in querySnapshot!!) {
                        tipo = "${document.getString("tipo")}"
                        //Comparar tipos
                        if (tipo.equals("AGRADABLES")) {
                            //COMPARAR TELEFONOS DE LA NUBE CON LOS DE LAS LLAMADAS PERDIDAS
                            if (it.equals(document.getString("celular"))) {
                                telefonTelAviv = document.getString("celular").toString()
                                SmsManager.getDefault().sendTextMessage(telefonTelAviv,null, mensajeAgradable,null,null)
                                println("Se envio el mensaje agradable al numero:${telefonTelAviv}")
                                //println("${document.getString("nombre")} esta en los ${tipo} con el numero: ${document.getString("celular")}")
                            }
                        } else {
                            if (it.equals(document.getString("celular"))) {
                                telefonTelAviv = document.getString("celular").toString()
                                SmsManager.getDefault().sendTextMessage(telefonTelAviv,null, mensajeDesagrable,null,null)
                                println("Se envio el mensaje desagradable al numero:${telefonTelAviv}")
                                //println("${document.getString("nombre")} esta en los ${tipo} con el numero: ${document.getString("celular")}")
                            }
                        }//FIN DEL ELSE
                    }//FIN FOR DOCUMENTOS EN LA NUBE
                }//FIN SNAPSHOT LISTENER
            }//FIN FOR LISTA TELEFONOS
        }//FIN ELSE
    }

    private fun listarLlamadas() {
        var llamadas = ArrayList<String>()
        val seleccion = CallLog.Calls.TYPE + "=" + CallLog.Calls.MISSED_TYPE
        var cursor = contentResolver.query(
            Uri.parse("content://call_log/calls"),
            null, seleccion, null, null
        )
        listaTelefonos.clear()
        var registro = ""
        while (cursor!!.moveToNext()){
            var nombre = cursor.getString(cursor.getColumnIndex(CallLog.Calls.CACHED_NAME))
            var numero = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER))
            numero = numero.replace(" ".toRegex(), "")
            var tipo = cursor.getString(cursor.getColumnIndex(CallLog.Calls.TYPE))
            var fecha = cursor.getString(cursor.getColumnIndex(CallLog.Calls.DATE))

            val seconds: Long = fecha.toLong()
            val formatter = SimpleDateFormat("dd-MM-yy HH:mm")
            val dateString: String = formatter.format(Date(seconds))

            registro = "NOMBRE: ${nombre} \nNUMERO: ${numero} \nTIPO: ${tipo} \nFECHA: ${dateString} \n"
            llamadas.add(registro)
            listaTelefonos.add(numero)
            println(listaTelefonos)
        }
        listallamadas.adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, llamadas
        )
        cursor.close()
    }

    private fun insertarContactos() {
        val builder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialogLayout = inflater.inflate(R.layout.add_contacts,null)
        var nombre = dialogLayout.findViewById<EditText>(R.id.nombrecontacto)
        var telefono = dialogLayout.findViewById<EditText>(R.id.celcontacto)
        var agradables = dialogLayout.findViewById<RadioButton>(R.id.rbtnagradable)
        var desagrables = dialogLayout.findViewById<RadioButton>(R.id.rbtndesagradable)

        with(builder){
            setTitle("Inserte los datos")
            setPositiveButton("OK"){ d,i->
                    if (agradables.isChecked){
                        insertarBD(nombre.text.toString(),telefono.text.toString(),agradables.text.toString())
                    }

                    if (desagrables.isChecked){
                        insertarBD(nombre.text.toString(),telefono.text.toString(),desagrables.text.toString())
                    }
            }
            setNegativeButton("Cerrar"){ d,i->}
                .setView(dialogLayout)
                .show()
        }
    }

    private fun insertarBD(nombre: String, tel: String, tipo: String) {
        var datosInsertar = hashMapOf(
            "nombre" to nombre,
            "celular" to tel,
            "tipo" to tipo,
        )

        baseRemota.collection("contactos")
            .add(datosInsertar)
            .addOnSuccessListener {
                alerta("EXITO! SE INSERTO CORRECTAMENTE")
            }
            .addOnFailureListener {
                mensaje("ERROR! no se pudo insertar")
            }
    }

    private fun mensaje(s: String) {
        AlertDialog.Builder(this).setTitle("ATENCION")
            .setMessage(s)
            .setPositiveButton("OK"){ d,i-> }
            .show()
    }

    private fun alerta(s: String) {
        Toast.makeText(this,s, Toast.LENGTH_LONG).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == siLlamada){ listarLlamadas() }
    }
}
