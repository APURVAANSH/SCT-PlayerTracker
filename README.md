# SCT-PlayerTracker

This is a tracker addon for [SimpleCompass](https://www.spigotmc.org/resources/simplecompass.63140/).
It allows to track players positions.

## How to install

- Drop the [jar file](https://github.com/arboriginal/SCT-PlayerTracker/releases) into your `plugins/SimpleCompass/trackers` folder
- Restart your server.

## Configuration

Edit the file `plugins/SimpleCompass/trackers/PlayerTracker.yml` (automatically created the first time the tracker is loaded).

Read [settings.yml](https://github.com/arboriginal/SCT-PlayerTracker/blob/master/src/settings.yml) to have a look on available parameters.

## Permissions

- To use this tracker, players must have:
    - **scompass.use**
    - **scompass.track**
    - **scompass.track.PLAYER** (or **scompass.track.***)
- To be able to track a player without his agreement, players also need:
    - **scompass.track.PLAYER.silently**
- To be able to request a player to track him, players also need:
    - **scompass.track.PLAYER.request**
- To be able to accept or deny tracking requests, players must have:
    - **scompass.track.PLAYER.accept**

**Optional:** (require you set `settings.autoload_target:true`)
    
- You can also use dynamic permissions to let players automatically track player(s) when they join:
    - **scompass.track.auto.PLAYER.`<name>`**
    - **scompass.track.auto.PLAYER.Notch** for example to auto track the player named "Notch"
- To auto track all players (without **scompass.track.auto.PLAYER.`<name>`** for each), players need:
    - **scompass.track.auto.PLAYER.***
