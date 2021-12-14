package com.nullpointer.streammusic.ui.fragments.details

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.nullpointer.streammusic.databinding.PlayerFragmentBinding
import com.nullpointer.streammusic.models.Song
import com.nullpointer.streammusic.presentation.SongsViewModel
import com.nullpointer.streammusic.services.exoPlayer.isPlaying
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@ExperimentalCoroutinesApi
class PlayerFragment : Fragment() {

    private var _binding: PlayerFragmentBinding? = null
    private val binding get() = _binding!!
    private val songsViewModel: SongsViewModel by activityViewModels()
    private var mCurrentPlaySong: Song? = null
    private var shouldUpdateSeekbar = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PlayerFragmentBinding.inflate(layoutInflater, container, false).apply {
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
        setOnClickListeners()
        setupSeekBar()
        addObservers()
    }

    private fun setupSeekBar()= with(binding){
        seekBar.setOnSeekBarChangeListener(object :SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    textCurrentTime.text=getTimeFormat(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                shouldUpdateSeekbar = false
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    songsViewModel.seekTo(it.progress.toLong())
                    shouldUpdateSeekbar = true
                }
            }

        })
    }

    private fun addObservers() = with(songsViewModel) {
        with(binding) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.CREATED) {
                    launch {
                        currentPlayingSong.collect { metadataCompat ->
                            mCurrentPlaySong = Song.fromMediaMetadata(metadataCompat)?.also {
                                textNameSong.text = it.title
                                textNameArtist.text = it.subtitle
                                imageSong.load(it.imgUrl)
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
                            seekBar.progress = it?.position?.toInt() ?: 0
                        }
                    }

                    launch{
                        currentPlayingPosition.collect {
                            if (shouldUpdateSeekbar) {
                                seekBar.progress = it.toInt()
                                textCurrentTime.text= getTimeFormat(it)
                            }
                        }
                    }

                    launch{
                        currentSongDuration.collect{
                            seekBar.max=it.toInt()
                            textTimeSong.text = getTimeFormat(it)
                        }
                    }
                }
            }
        }
    }

    private fun getTimeFormat(ms: Long): String = with(binding){
        val dateFormat = SimpleDateFormat("mm:ss", Locale.getDefault())
        return  dateFormat.format(ms)
    }

    private fun setOnClickListeners() = with(binding) {
        containerButtonplay.setOnClickListener {
            mCurrentPlaySong?.let {
                songsViewModel.playOrToggleSong(it, true)
                if (songsViewModel.playbackState.value?.isPlaying == true) {
                    buttonPlay.setMinAndMaxProgress(.5f, 1f)
                } else {
                    buttonPlay.setMinAndMaxProgress(0f, .5f)
                }
                buttonPlay.playAnimation()
            }
        }

        containerButtonNext.setOnClickListener {
            songsViewModel.skipToNextSong()
            buttonNext.playAnimation()
        }

        containerButtonPrev.setOnClickListener {
            songsViewModel.skipToPreviousSong()
            buttonPrev.playAnimation()
        }
    }
}