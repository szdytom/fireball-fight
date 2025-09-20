__config() -> {
    'strict' -> true,
    'scope' -> 'global',
	'commands' -> {
		'start' -> 'commandStart',
		'stop' -> 'commandStop',
		'relocate' -> 'commandRelocate',
		'relocatehere' -> 'commandRelocateHere',
		'reward <selection> <rewradVer>' -> 'commandReward'
	},
	'arguments' -> {
		'selection' -> {
			'type' -> 'int',
			'min' -> 0,
			'max' -> 3,
			'suggest' -> [0, 1, 2, 3]
		},
		'rewradVer' -> {
			'type' -> 'int',
		}
	}
};

global_playerDatMap = {};
global_timeCounter = 0;
global_gameRunning = false;
global_multiAngleSin = sin(15);
global_multiAngleCos = cos(15);

shuffleList(varList) -> (
	len = length(varList);
    if (length(varList) <= 1, varList,
        c_for(i = len - 1, i >= 1, i = i - 1,
            random_index = floor(rand(i + 1));
            
            // swap elements
            temp = varList:i;
            varList:i = varList:random_index;
            varList:random_index = temp;
        );
        varList
    )
);

__on_start() -> (
    run('gamerule keepInventory true');
    run('gamerule spawnRadius 50');
	run('gamerule doMobSpawning false');
	run('gamerule doDaylightCycle false');
    run('bossbar remove fbf:time_counter');
);

touchPlayerDat(myuuid) -> (
    if (!has(global_playerDatMap, myuuid), (
        put(global_playerDatMap, myuuid, {
            'lastUse' -> -500,
            'fbPower' -> 2,
            'cooldown' -> 100,
			'fbSpeed' -> 1,
			'multishot' -> false,
            'reward' -> 0,
			'points' -> 0,
			'nextChoices' -> null,
			'rewardVer' -> 1,
			'armorLvl' -> 0,
			'hasSharpSword' -> false
        });
    ));
);

tickToTime(ticks) -> (
	if (ticks < 1200, (
		return(str('%d 秒', ticks / 20));
	));
    if (ticks % 1200 == 0, (
        return(str('%d 分钟', ticks / 1200));
    ));
    return(str('%d 分钟 %d 秒', floor(ticks / 1200), (ticks / 20) % 60));
);

relocateAt(x, y) -> (
	print('游戏中心点已设定为：' + x + ', ' + y);
	run(str('setworldspawn %d 400 %d', x, y));
	run(str('spreadplayers %d %d 20 50 false @a', x, y));
);

commandRelocate() -> (
	centerX = floor(rand(29000000));
	centerZ = floor(rand(29000000));
	relocateAt(centerX, centerZ);
	return(true);
);

commandRelocateHere() -> (
	myself = player();
	pos = myself ~ 'pos';
	relocateAt(floor(pos:0), floor(pos:2));
	return(true);
);

commandStop() -> (
	if (!global_gameRunning, (
		print('游戏未在进行中，无法停止！');
		return(false);
	));

	global_gameRunning = false;
	run('bossbar remove fbf:time_counter');
	updateScoreboard();
	display_title(player('all'), 'title', '游戏已被管理员停止！');
	return(true);
);

commandStart() -> (
	if (global_gameRunning, (
		print('游戏已经在进行中，无法重新开始！');
		return(false);
	));

	global_gameRunning = true;
	global_playerDatMap = {};

    run('clear @a');
	schedule(0, 'prepareGameCountdown', 60);

	return(true);
);

prepareGameCountdown(remainTicks) -> (
	display_title(player('all'), 'title', '游戏即将开始！');
	display_title(player('all'), 'subtitle', '剩余时间：' + tickToTime(remainTicks) + '，请准备好！');
	if (remainTicks <= 20, (
		schedule(20, 'startGame');
		return(false);
	));
	schedule(20, 'prepareGameCountdown', remainTicks - 20);
);

startGame() -> (
    run('give @a spyglass');
    run('give @a bread 64');
	run('give @a hay_block 64');

    global_timeCounter = 6000; // 5 min
    bossbar('fbf:time_counter');
    bossbar('fbf:time_counter', 'max', global_timeCounter);
	bossbar('fbf:time_counter', 'value', global_timeCounter);
    bossbar('fbf:time_counter', 'name', '倒计时：' + tickToTime(global_timeCounter));
	bossbar('fbf:time_counter', 'players', player('all'));
	schedule(20, 'countdownCallback');

	run('scoreboard objectives remove fbf_points');
	run('scoreboard objectives add fbf_points dummy "积分排行"');
	run('scoreboard objectives setdisplay sidebar fbf_points');
	updateScoreboard();

	display_title(player('all'), 'title', '游戏开始！');
	display_title(player('all'), 'subtitle', '');
);

countdownCallback() -> (
	global_timeCounter += -20;
	bossbar('fbf:time_counter', 'value', global_timeCounter);
	bossbar('fbf:time_counter', 'name', '倒计时：' + tickToTime(global_timeCounter));
	if (global_timeCounter <= 0, (
		global_gameRunning = false;
		run('bossbar remove fbf:time_counter');
		updateScoreboard();
		display_title(player('all'), 'title', '游戏结束！');
		return(false);
	));
	schedule(20, 'countdownCallback');
	return(true);
);

shootFireballEntity(myself, fbPower, playerPos, fbM) -> (
	fireballTags = str('{ExplosionPower: %db, acceleration_power: 0.3d, Motion: [%fd,%fd,%fd]}'
        , fbPower, fbM:0, fbM:1, fbM:2);
    fireball = spawn('fireball', playerPos + fbM * 2, nbt(fireballTags));
    modify(fireball, 'nbt_merge', {'Owner' -> myself ~ 'nbt' : 'UUID'}); 
);

shootFireball(myself) -> (
    myuuid = myself ~ 'uuid';
    touchPlayerDat(myuuid);

    nowTT = tick_time();
    cooldownTime = global_playerDatMap:myuuid:'cooldown';
    dTime = nowTT - global_playerDatMap:myuuid:'lastUse';
    if (dTime < cooldownTime, (
        display_title(player(), 'actionbar'
            , str('冷却中，请再等待 %.2f 秒以再次发射'
            , (cooldownTime - dTime) / 20.0));
        return(false);
    ));
    global_playerDatMap:myuuid:'lastUse' = nowTT;

    playerPos = (myself ~ 'pos') + [0, myself ~ 'eye_height', 0];
    fbM = myself ~ 'motion';

	speedMul = global_playerDatMap:myuuid:'fbSpeed';
    v = query(myself, 'look');
	v = v * speedMul;
    fbM:0 += v:0;
    fbM:1 = v:1;
    fbM:2 += v:2;
	fbPower = global_playerDatMap:myuuid:'fbPower';
	shootFireballEntity(myself, fbPower, playerPos, fbM);
	if(global_playerDatMap:myuuid:'multishot', (
		fbM = myself ~ 'motion';
		vRotL = [v:0 * global_multiAngleCos - v:2 * global_multiAngleSin, v:1, v:0 * global_multiAngleSin + v:2 * global_multiAngleCos];
		shootFireballEntity(myself, fbPower, playerPos, fbM + vRotL);
		vRotR = [v:0 * global_multiAngleCos + v:2 * global_multiAngleSin, v:1, -v:0 * global_multiAngleSin + v:2 * global_multiAngleCos];
		shootFireballEntity(myself, fbPower, playerPos, fbM + vRotR);
	));
    return(true);
);

__on_player_releases_item(myself, item_tuple, hand) -> (
    if(item_tuple:0 == 'spyglass',
        shootFireball(myself);
    );
);

__on_player_uses_item(myself, item_tuple, hand) -> (
	if(item_tuple:0 == 'amethyst_shard',
		shootFireball(myself);
	);
);

askReward(myself) -> (
	myuuid = myself ~ 'uuid';
	touchPlayerDat(myuuid);
	if (global_playerDatMap:myuuid:'reward' <= 0, (
		return(false);
	));
	updateNextChoices(myuuid);
	print('可用升级点数：' + global_playerDatMap:myuuid:'reward' + '，可选升级：');
	for(global_playerDatMap:myuuid:'nextChoices', (
		print(format(' + ', str('mb [%s]', _:0), str('!/fireball-fight reward %d %d', _i, global_playerDatMap:myuuid:'rewardVer')));
	));
);

commandReward(i, ver) -> (
	if (!global_gameRunning, (
		print('游戏未开始，无法升级！');
		return(false);
	));

	myself = player();
	myuuid = myself ~ 'uuid';
	touchPlayerDat(myuuid);

	if (global_playerDatMap:myuuid:'rewardVer' != ver, (
		print('请勿重复使用旧版本升级指令！');
		return(false);
	));

	if (global_playerDatMap:myuuid:'reward' <= 0, (
		print('没有可用的升级点数！');
		return(false);
	));

	choices = global_playerDatMap:myuuid:'nextChoices';
	if (i < 0 || i >= length(choices), (
		print('无效的选择！');
		askReward(myself);
		return(false);
	));

	choice = choices:i;
	call(choice:1, myself);
	global_playerDatMap:myuuid:'reward' += -1;
	global_playerDatMap:myuuid:'nextChoices' = null;
	global_playerDatMap:myuuid:'rewardVer' += 1;
	print('已选择升级：' + choice:0 + '，剩余升级点数：' + global_playerDatMap:myuuid:'reward');
	if (global_playerDatMap:myuuid:'reward' > 0, (
		askReward(myself);
	));
	return(true);
);

__on_statistic(myself, category, event, value) -> (
    if(event == 'player_kills' && global_gameRunning, (
        myuuid = myself ~ 'uuid';
        touchPlayerDat(myuuid);
        global_playerDatMap:myuuid:'reward' += 1;
		askReward(myself);
		global_playerDatMap:myuuid:'points' += 10;
		updateScoreboard();
    ));

	if(event == 'deaths' && global_gameRunning, (
		myuuid = myself ~ 'uuid';
		touchPlayerDat(myuuid);
		global_playerDatMap:myuuid:'points' += -3;
		updateScoreboard();
	));
);

__on_player_drops_item(myself) -> (
	item_tuple = myself ~ 'holds';
	if (item_tuple:0 == 'spyglass' || item_tuple:0 == 'amethyst_shard', (
		display_title(myself, 'actionbar', '不建议丢弃此物品，使用Ctrl-Q强制丢弃。');
		inventory_remove(myself, item_tuple:0);
		run('give ' + myself ~ 'name' + ' ' + item_tuple:0);
		return('cancel');
	));
);

updateScoreboard() -> (
	for(player('all'), (
		myuuid = _ ~ 'uuid';
		touchPlayerDat(myuuid);
		scoreboard('fbf_points', _ ~ 'name', global_playerDatMap:myuuid:'points');
	));
);

updateNextChoices(myuuid) -> (
	if (global_playerDatMap:myuuid:'nextChoices' == null, (
		global_playerDatMap:myuuid:'rewardVer' += 1;
		choices = [];
		choiceFuncs = ['choiceFbPower', 'choiceFbCooldown', 'choiceIncreaseFbSpeed', 'choiceMultishot', 'choiceExtraPoints', 'choiceGoldApple', 'choiceObsidian', 'choiceSword', 'choiceEnderPearl', 'choiceUpdateArmor'];
		for (choiceFuncs, (
			choice = call(_, myuuid);
			if (choice != false, (
				choices += choice;
			));
		));
		choices = shuffleList(choices);
		global_playerDatMap:myuuid:'nextChoices' = [];
		c_for(i = 0, i < 4, i += 1, (
			if (i < length(choices), (
				global_playerDatMap:myuuid:'nextChoices' += choices:i;
			));
		));
	));
);

choiceFbPower(myuuid) -> (
	fbPower = global_playerDatMap:myuuid:'fbPower';
	if (fbPower < 5 && global_playerDatMap:myuuid:'cooldown' > 0, (
		return([str('升级火球威力到 %d 级', fbPower + 1), 'bounceFbPower']);
	));
	return(false);
);

bounceFbPower(myself) -> (
	myuuid = myself ~ 'uuid';
	global_playerDatMap:myuuid:'fbPower' += 1;
);

choiceFbCooldown(myuuid) -> (
	if (global_playerDatMap:myuuid:'multishot', (
		return(false);
	));
	cooldownTime = global_playerDatMap:myuuid:'cooldown';
	if (cooldownTime > 20, (
		return([str('减少冷却时间为 %d 秒', (cooldownTime / 20) - 1), 'reduceFbCooldown']);
	));
	if (cooldownTime == 20 && global_playerDatMap:myuuid:'fbPower' == 2, (
		return(['彻底冷却', 'reduceFbCooldown']);
	));
	return(false);
);

reduceFbCooldown(myself) -> (
	myuuid = myself ~ 'uuid';
	global_playerDatMap:myuuid:'cooldown' += -20;
	if (global_playerDatMap:myuuid:'cooldown' == 0, (
		inventory_remove(myself, 'spyglass');
		run('give ' + myself ~ 'name' + ' amethyst_shard');
	));
);

choiceMultishot(myuuid) -> (
	if (!global_playerDatMap:myuuid:'multishot' && global_playerDatMap:myuuid:'fbPower' == 5 && global_playerDatMap:myuuid:'cooldown' == 100, (
		return(['多重射击', 'enableMultishot']);
	));
	return(false);
);

enableMultishot(myself) -> (
	myuuid = myself ~ 'uuid';
	global_playerDatMap:myuuid:'multishot' = true;
	inventory_remove(myself, 'spyglass');
	run('give ' + myself ~ 'name' + ' amethyst_shard');
);

choiceIncreaseFbSpeed(myuuid) -> (
	fbSpeed = global_playerDatMap:myuuid:'fbSpeed';
	if (fbSpeed >= 4, (
		return(false);
	));
	return([str('使火球飞行速度为标准的 %.1f 倍', fbSpeed + 1), 'increaseFbSpeed']);
);

increaseFbSpeed(myself) -> (
	myuuid = myself ~ 'uuid';
	global_playerDatMap:myuuid:'fbSpeed' += 1;
);

choiceExtraPoints(myuuid) -> (
	return(['直接获得 5 积分', 'gainExtraPoints']);
);

gainExtraPoints(myself) -> (
	myuuid = myself ~ 'uuid';
	global_playerDatMap:myuuid:'points' += 5;
	updateScoreboard();
);

choiceGoldApple(myuuid) -> (
	['兑换附魔金苹果 x3', 'exchangeGoldenApple']
);

exchangeGoldenApple(myself) -> (
	run('give ' + myself ~ 'name' + ' enchanted_golden_apple 3')
);

choiceObsidian(myuuid) -> (
	['兑换黑曜石 x24', 'exchangeObsidian']
);

exchangeObsidian(myself) -> (
	run('give ' + myself ~ 'name' + ' obsidian 24')
);

choiceSword(myuuid) -> (
	if(global_playerDatMap:myuuid:'hasSharpSword', (
		return(false);
	));
	return(['兑换锋利的剑', 'exchangeSword']);
);

exchangeSword(myself) -> (
	run('give ' + myself ~ 'name' + ' diamond_sword[attribute_modifiers=[{type:"attack_damage", id:"minecraft:base_attack_damage", slot:"mainhand", amount:18.5, operation:"add_value"}]]');
	myuuid = myself ~ 'uuid';
	global_playerDatMap:myuuid:'hasSharpSword' = true;
);

choiceEnderPearl(myuuid) -> (
	['兑换末影珍珠 x8', 'exchangeEnderPearl']
);

exchangeEnderPearl(myself) -> (
	run('give ' + myself ~ 'name' + ' ender_pearl 8')
);

choiceUpdateArmor(myuuid) -> (
	curLvl = global_playerDatMap:myuuid:'armorLvl';
	if(curLvl == 0, (
		return(['装备锁链甲', 'rewardEquipArmor']);
	));
	if(curLvl == 1, (
		return(['升级为铁甲', 'rewardEquipArmor']);
	));
	if(curLvl == 2, (
		return(['升级为钻石甲', 'rewardEquipArmor']);
	));
	return(false);
);

playerEquipArmor(myself, armorPrefix) -> (
	inventory_set('equipment', player(), 4, 1, null, str('{id:"%s_helmet",components:{"minecraft:unbreakable":{}}}', armorPrefix));
	inventory_set('equipment', player(), 3, 1, null, str('{id:"%s_chestplate",components:{"minecraft:unbreakable":{}}}', armorPrefix));
	inventory_set('equipment', player(), 2, 1, null, str('{id:"%s_leggings",components:{"minecraft:unbreakable":{}}}', armorPrefix));
	inventory_set('equipment', player(), 1, 1, null, str('{id:"%s_boots",components:{"minecraft:unbreakable":{}}}', armorPrefix));
);

rewardEquipArmor(myself) -> (
	myuuid = myself ~ 'uuid';
	curLvl = global_playerDatMap:myuuid:'armorLvl';
	if(curLvl == 0, (
		playerEquipArmor(myself, 'chainmail');
		global_playerDatMap:myuuid:'armorLvl' = 1;
		return(true);
	));
	if(curLvl == 1, (
		playerEquipArmor(myself, 'iron');
		global_playerDatMap:myuuid:'armorLvl' = 2;
		return(true);
	));
	if(curLvl == 2, (
		playerEquipArmor(myself, 'diamond');
		global_playerDatMap:myuuid:'armorLvl' = 3;
		return(true);
	));
	return(false);
);
