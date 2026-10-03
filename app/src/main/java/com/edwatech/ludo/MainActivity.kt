package com.edwatech.ludo

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LudoMainScreen()
        }
    }
}

@Composable
fun LudoMainScreen() {
    val context = LocalContext.current
    var showAdminDialog by remember { mutableStateOf(false) }
    var adminCodeInput by remember { mutableStateOf("") }
    val ADMIN_SECRET_CODE = "Jesus001#"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp)
    ) {
        // Branding STEEVENS x EdwaTech-HT
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "LUDO EXPRESS",
                color = Color(0xFFFFD700),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Created by STEEVENS x EdwaTech-HT",
                color = Color(0xFFAAAAAA),
                fontSize = 14.sp
            )
        }

        // Bouton Prensipal yo
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    Toast.makeText(context, "N ap louvri sal Ludo an tan reyèl...", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text(text = "JWE LUDO (ONLINE)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    Toast.makeText(context, "N ap chaje Top 200 rejyon an...", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
            ) {
                Text(text = "KLASMAN REJYONAL (TOP 200)", fontSize = 14.sp, color = Color.White)
            }
        }

        // Bouton Aksè Admin
        Button(
            onClick = { showAdminDialog = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222))
        ) {
            Text(text = "🔒 Admin Panel", color = Color(0xFF888888), fontSize = 12.sp)
        }

        // Pop-up Kòd Admin (Jesus001#)
        if (showAdminDialog) {
            AlertDialog(
                onDismissRequest = { showAdminDialog = false },
                title = { Text("🔒 Aksè Swenyal Admin") },
                text = {
                    Column {
                        Text("Antre kòd sekrè pou debloke Panèl Admin STEEVENS x EdwaTech-HT:")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = adminCodeInput,
                            onValueChange = { adminCodeInput = it },
                            label = { Text("Kòd Sekrè") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (adminCodeInput == ADMIN_SECRET_CODE) {
                            Toast.makeText(context, "Aksè Okòde! Byenveni STEEVENS.", Toast.LENGTH_LONG).show()
                            showAdminDialog = false
                            adminCodeInput = ""
                        } else {
                            Toast.makeText(context, "❌ Kòd envalib!", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("Debloke")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showAdminDialog = false
                        adminCodeInput = ""
                    }) {
                        Text("Anule")
                    }
                }
            )
        }
    }
}