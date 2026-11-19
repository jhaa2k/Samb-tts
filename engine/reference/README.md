Put your reference voice here:
- ref.wav : clean 5-10 s, 24 kHz mono, one native Sambalpuri speaker, no noise/music.
            Use only a voice you have explicit consent to use.
- ref.txt : the EXACT words spoken in ref.wav, written in Odia script.
Convert: ffmpeg -i in.wav -ar 24000 -ac 1 ref.wav
