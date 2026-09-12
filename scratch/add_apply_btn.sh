#!/bin/bash
sed -i '/BasicTextField(/,/modifier = Modifier.fillMaxWidth()/c\
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {\
                                BasicTextField(\
                                    value = directValueText,\
                                    onValueChange = { newTxt -> directValueText = newTxt },\
                                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold),\
                                    keyboardActions = KeyboardActions(onDone = { \
                                        onValueTyped(directValueText)\
                                        onDismiss()\
                                    }),\
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),\
                                    singleLine = true,\
                                    modifier = Modifier.weight(1f)\
                                )\
                                IconButton(onClick = {\
                                    onValueTyped(directValueText)\
                                    onDismiss()\
                                }, modifier = Modifier.size(24.dp)) {\
                                    Icon(imageVector = Icons.Default.Check, contentDescription = "Apply", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))\
                                }\
                            }' app/src/main/kotlin/com/sbf/lightspeed/settings/SettingsDialogComponents.kt
