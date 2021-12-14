package com.nullpointer.streammusic.ui.fragments.main

import android.animation.LayoutTransition
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.load
import com.nullpointer.streammusic.core.states.Resource
import com.nullpointer.streammusic.core.utils.hide
import com.nullpointer.streammusic.core.utils.show
import com.nullpointer.streammusic.core.utils.showSnack
import com.nullpointer.streammusic.databinding.SongsFragmentBinding
import com.nullpointer.streammusic.models.Song
import com.nullpointer.streammusic.presentation.SongsViewModel
import com.nullpointer.streammusic.services.exoPlayer.isPlaying
import com.nullpointer.streammusic.ui.adapters.ListSongAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber

@ExperimentalCoroutinesApi
@AndroidEntryPoint
class SongsFragment : Fragment() {

    private var _binding: SongsFragmentBinding? = null
    private val binding get() = _binding!!

    private val songsViewModel: SongsViewModel by activityViewModels()

    private var currentPlaySong: Song? = null

    private val songsAdapter by lazy {
        ListSongAdapter() {
            songsViewModel.playOrToggleSong(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SongsFragmentBinding.inflate(layoutInflater, container, false).apply {
            containerSongs.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
            if (songsViewModel.playbackState.value?.isPlaying == true) {
                buttonPlay.progress = 0.5f
            }
        }
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        addObservers()
        setOnClicksListeners()
    }

    private fun setOnClicksListeners() = with(binding) {
        containerPlay.setOnClickListener {
            findNavController().navigate(
                SongsFragmentDirections.actionSongsFragmentToPlayerFragment()
            )
        }
        buttonPlay.setOnClickListener {
            currentPlaySong?.let {
                containerPlay.show()
                songsViewModel.playOrToggleSong(it, true)
            }
        }
    }

    private fun addObservers() = with(songsViewModel) {
        with(binding) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.CREATED) {

                    launch {
                        currentPlayingSong.collect { metadataCompat ->
                            Song.fromMediaMetadata(metadataCompat)?.also {
                                updateTitleAndSongImage(it)
                            }
                        }
                    }

                    launch {
                        listSongByUser.collect { state ->
                            when (state) {
                                is Resource.Loading -> progressBar.show()
                                is Resource.Failure -> Unit
                                is Resource.Success -> {
                                    Timber.d("numero de canciones obtenidad ${state.data.size}")
                                    progressBar.hide()
                                    containerPlay.show()
                                    state.data.let { listSongs ->
                                        songsAdapter.submitList(listSongs)
                                        if (currentPlaySong == null && listSongs.isNotEmpty()) {
                                            currentPlaySong = listSongs[0]
                                        }
                                    }

                                }
                            }
                        }
                    }
                    launch {
                        isConnected.collect {
                            when (it) {
                                is Resource.Failure -> {
                                    Timber.e("Error al conectar el servicio $it")
                                    showSnack("Error desconocido")
                                }
                                is Resource.Loading -> {
                                    Timber.d("Conectado el servicio")
                                }
                                is Resource.Success -> {
                                    Timber.d("Servicio conectado con exito")
                                }
                            }
                        }
                    }

                    launch {
                        networkError.collect {
                            when (it) {
                                is Resource.Failure -> {
                                    Timber.e("Error de conexion a intener $it")
                                    showSnack("Verifiquer su conexion a intenet")
                                }
                                is Resource.Loading -> {
                                    Timber.d("Intentando la conexion a internet")
                                }
                                is Resource.Success -> {
                                    Timber.d("Conexion exitosa")
                                }
                            }
                        }
                    }

                    launch {
                        playbackState.collect {

                            if (it?.isPlaying == true) {
                                buttonPlay.setMinAndMaxProgress(0f, .5f)
                            } else {
                                buttonPlay.setMinAndMaxProgress(.5f, 1f)
                            }
                            buttonPlay.playAnimation()
                        }
                    }
                }
            }
        }
    }

    private fun updateTitleAndSongImage(song: Song)= with(binding){
        imgCurrentSong.load(song.imgUrl)
        textNameCurrentSong.text=song.title
    }


    private fun setupRecyclerView() = with(binding) {
        recyclerSongs.adapter = songsAdapter
    }
}