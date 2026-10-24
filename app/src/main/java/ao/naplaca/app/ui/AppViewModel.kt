package ao.naplaca.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ao.naplaca.app.AppContainer
import ao.naplaca.app.NaPlacaApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as NaPlacaApp).container

/** Cria um ViewModel com acesso ao [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}
