# All-In-One-Thoroughbred-Mod

An all-in-one thoroughbred mod that introduces the breeding, training, and racing of horses. Tailored to capture realism and adrenaline.

Source Code: https://github.com/AIOThoroughbredMod/All-In-One-Thoroughbred-Mod

## Overview

The ultimate racehorse mod is here, combining breeding, training, and racing into one cohesive system. Breed champion horses with a true pedigree, train them to reach greater heights, and test your efforts on the track. Designed for realism, these are no longer average vanilla horses that can be bossed around without consequence. Every decision matters: one wrong choice could mean another day for your prized horse or the end of its life. Make your decisions wisely.

This mod is available in English, Traditional/Simplified Chinese and Japanese.

\======================================================================

### \== HOW DO I GET STARTED? ==

When creating a new world, you are prompted to choose a World Equine Time Flow, which determines how horse years are calculated in your world:

*   **One-Year Time Flow (Default):** Tailored for servers attempting full realism. Foals grow up and mares go into heat on a real-life time scale.
*   **Season Time Flow (3/4-Month Cycles):** An equine year is counted per 3 or 4 months, with each new year called a "season." This option is tailored for servers that want faster progression within a reasonable timescale evenly divisible across 12 months.
*   **Custom Time Flow:** Set up your own custom time flow if default options do not suit your server.

> **WARNING:** Once a time flow is chosen for a world, it cannot be changed. Choose wisely.

\======================================================================

### \== I'VE CHOSEN A TIME FLOW, WHAT NOW? ==

This mod utilizes the _Lightman's Currency_ mod as a dependency for its economy system. Without funds, you cannot become a registered horse owner, though you can still tame wild vanilla horses to prepare for what lies ahead.

1.  **Earn Funds:** Copper coins count as $1. Earn at least $10,000.
    
2.  **Deposit Currency:** Use `/tb cashin` to deposit money into your bank once you reach $10,000. Use `/tb cashout` to convert bank balance back into physical coins.
    
3.  **Register Ownership:** Enter the `/tb owner` command to open the registration form GUI. Complete the form and pay the $10,000 registration fee.
    
4.  **Subsequent Edits:** Registration details can be modified by entering `/tb owner` again, though editing incurs an additional fee. Ensure all details are correct during initial registration.
    
Currency has additional uses beyond registration, left for players to discover.

\======================================================================

### \== I'VE REGISTERED TO BECOME A HORSE OWNER, WHAT NOW? ==

1.  Craft a **Thoroughbred Registry Form** and use it on a tamed vanilla horse (or go out and tame one).
2.  After using the registry form, a registration GUI opens, requiring you to name your horse and select its gender.
3.  Choose a naming language rule from English, Chinese, or Japanese, which follow the naming regulations of the British Horseracing Authority, Hong Kong Jockey Club, and Japan Racing Association, respectively.
4.  Once named and gendered, the horse becomes a registered thoroughbred. While it appears identical to a vanilla horse outwardly, its core mechanics and AI are entirely overhauled. Mount it with a saddle to experience the new handling.
5.  Completing registration opens the **Thoroughbred Stat Card GUI**, displaying full equine information. Review this information at any time by sneaking and right-clicking the horse.

\======================================================================

### \== I'VE REGISTERED THE HORSE AS A THOROUGHBRED, WHAT NOW? ==

From horse training and racing to daily care and breeding, the path forward is yours to navigate. Thoroughbred controls differ significantly from vanilla horses.

Press **G** to enable **Training Mode**, allowing you to train five core statistics:

*   **Speed:** Determines how fast a horse can run.
*   **Stamina:** Acts as the horse's fuel, determining how long it can sustain running.
*   **Power:** Affects acceleration and the ability to climb uphill slopes. It enables powerful strides that serve as a brief speed boost.
*   **Guts:** When stamina depletes, the horse taps into its guts reserves. This prevents further acceleration and dictates how long the horse can maintain its current speed. Higher guts reduce the rate of speed loss once both stamina and guts are exhausted.
*   **Wits:** Affects breathing rhythm and reaction to the **Racing Crop**, an item signaling the horse to put forth maximum effort. Low wits can cause ragged breathing, unnecessary stamina drain, and require more urges from the crop to reach top speed.

\======================================================================

### \== WHAT ARE THESE BARS WHEN I RIDE ON MY HORSE? ==

*   **SPD (Speed):** Displays current running speed. The red bar indicates the comfortable running zone, acting as a soft cap until the Racing Crop is used. The grey bar represents the absolute speed hard cap.
*   **STM (Stamina):** Represents stamina reserves, which begin depleting once speed exceeds 35 km/h. When the horse's breath is ragged, the bar flashes between red and blue, causing accelerated stamina drain. Once fully depleted, the GUTS bar replaces it.
*   **GUTS:** Appears after stamina drops to zero. It halts further acceleration but keeps the current speed stable until it ultimately depletes.
*   **Distance Aptitude (Displayed as numbers above the SPD bar):** The distance aptitude mechanics utilize a four-zone structure:
    *   **Zone 0 (Pre-Minimum Warm-Up):** Handles initial acceleration before hitting the minimum distance required, which actually is defined by the horse's shown minimum minus 500m.
    *   **Zone 1 (Optimal Range):** The ideal distance window for the horse.
    *   **Zone 2 (Buffer Zone):** A stamina-taxing buffer outside the ideal range, lasting for 200m.
    *   **Zone 3 (Over-Distance Wall):** A severe over-distance threshold causing accelerated drain after passing the buffer zone. Not only does stamina deplete tremendously fast, the horse's max speed cap will drop (it's temporary so don't worry about this ruining a horse!).
*   **HNG & THR (Hunger and Hydration):** If a horse is starving or dehydrated, training gains are halted. Feed horses horse food and water. Horses automatically consume hay bales placed on the floor and water inside cauldrons. Feeding an enchanted golden apple pauses hunger and thirst progression for two real-life days.
*   **FTG (Fatigue):** A critical meter to monitor. Reaching 50% fatigue increases susceptibility to illness and injury. Surpassing 80% enters the danger zone, inflicting the **Unstable Legs** ailment, which spikes injury risks and can cause catastrophic consequences. Fatigue is reduced by brushing with the **Grooming Brush** or allowing the horse to rest. Feeding an enchanted golden apple reduces fatigue and heavily suppresses its progression for two real-life days, though it does not outright stop it.
*   **BND (Bond):** Caring for your horse increases bond and mood, granting training bonuses.
*   **MOOD:** Affects training efficiency and race performance. Keep your horse happy.

\======================================================================

### \== MY HORSE GOT SICK/INJURED, WHAT NOW? ==

Oh no! It seems your horse is in a prolonged state of high fatigue! Prolonged high fatigue can lead to illness or injury, so make sure to keep an eye on it!

*   **Veterinary Treatment:** Type `/tb vet` to charge fees and treat your horse as long as funds are sufficient. The vet cannot instantly cure injuries, but increases recovery rates and boosts full recovery odds. Injuries risk permanently reducing or removing racing ability, which veterinary care mitigates.
*   **Catastrophic Fractures:** If a horse suffers a catastrophic fracture resulting in a **poor prognosis**, it cannot be saved, even by a vet. It lives in excruciating pain on borrowed time. Each day carries a 50/50 chance of passing away from the pain, accompanied by a spine-chilling wither sound, which indicates the horse dying in resentment. Euthanizing the horse via `/tb euthanize` ends its suffering with dignity.
*   **Miracle Recovery:** There is a 1% chance of a miracle recovery. Feeding an enchanted golden apple lessens pain and prevents death checks for the day. Consistent feeding may eventually trigger a recovery, though racing ability will likely be lost due to severe leg damage. Normal golden apples achieve a similar effect with diminishing returns, meaning each successive apple removes less death chance from daily rolls. If the horse ultimately passes, it dies with less pain and without resentment.
 
\======================================================================

### \== COULD MY HORSE LIVE FOREVER? ==
 
All horses eventually succumb to old age. The risk of entering **Death's Door** begins at **25 Equine Years**, with mortality chances increasing each subsequent year.

When a horse reaches Death's Door, it lies down similarly to a horse with a poor prognosis, but without physical pain. High bond levels cause the horse to emit heart particles as a final farewell. A daily mortality check rolls a 50/50 chance of survival. Aside from admin intervention, passing away from old age is inevitable, __**after which the horse departs peacefully.**__

\======================================================================

### \== AI DISCLAIMER ==

This mod is entirely vibe-coded by Gemini 4 Argon (as of November 19th, Gemini 4 Neon) out of desperation - no mod developers want to do my mod even when I commission them, probably because it's too ambitious and complicated. In addition, I lack coding knowledge and tried, but ultimately could not learn how to code in Java, so I definitely can't make this mod on my own.

However, custom item and block textures are done entirely by hand.

Because the code is made by AI instead of me, the source code will be public, and anyone can modify it to their heart's content. No credit is needed whatsoever, I just want this damn thing to become real!

\======================================================================
