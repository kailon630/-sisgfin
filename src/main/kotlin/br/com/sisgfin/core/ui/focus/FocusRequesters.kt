package br.com.sisgfin.core.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester

@Composable
fun rememberPanelFocusRequester(): FocusRequester = remember { FocusRequester() }
