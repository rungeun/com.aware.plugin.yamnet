AWARE: YamNet
===================================

This plugin uses Google's YamNet model to classify ambient audio into 521 different sound categories in real-time. 

# Settings
- **status_plugin_yamnet**: (boolean) activate/deactivate YamNet plugin
- **frequency_plugin_yamnet**: (integer) interval between audio data snippets, in minutes. Default value is every 5 minutes.
- **duration_plugin_yamnet**: (integer) Audio recording duration in milliseconds. Default is 1000ms.
- **save_audio_files**: (boolean) Save raw audio files to permanent storage. Default is false for privacy concerns.

# Broadcasts
**ACTION_AWARE_PLUGIN_YAMNET**
Broadcast when audio is analyzed, with the following extras:
- **timestamp**: (long) unix timestamp in milliseconds
- **duration**: (integer) recording duration in milliseconds  
- **analysis_results**: (string) JSON array containing classification results with label names and confidence scores
    
# Providers
## YamNet Analysis Data
> content://com.aware.plugin.yamnet.provider.yamnet/plugin_yamnet

Field | Type | Description
----- | ---- | -----------
_id | INTEGER | primary key auto-incremented
timestamp | REAL | unix timestamp in milliseconds of sample
device_id | TEXT | AWARE device ID
duration | INTEGER | recording duration in milliseconds
analysis_results | TEXT | JSON array with classification results (label, score pairs)

## YamNet Audio Data (Local Only)
> content://com.aware.plugin.yamnet.provider.yamnet/plugin_yamnet_audio

Field | Type | Description
----- | ---- | -----------
_id | INTEGER | primary key auto-incremented
timestamp | REAL | unix timestamp in milliseconds of sample
device_id | TEXT | AWARE device ID
duration | INTEGER | recording duration in milliseconds
raw_audio | BLOB | raw audio data (only if save_audio_files is enabled)

## License

This project is a modified version of the [AWARE ambient_noise plugin](https://github.com/denzilferreira/com.aware.plugin.ambient_noise), 
which is part of the [AWARE Framework](https://github.com/awareframework/aware-client).

- Original work: Copyright (c) 2011 AWARE Mobile Context Instrumentation Middleware/Framework  
- Modifications: Copyright (c) 2025 RunGeun

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) file for details.