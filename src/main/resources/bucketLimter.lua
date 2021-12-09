-- key 存储桶内剩余令牌数量
local bucketKey = KEYS[1]

-- key 记录上次调用本接口时间
local last_mill_request_key = KEYS[2]

-- 桶内允许的最大令牌数  也是初始化令牌数
local limit = tonumber(ARGV[1])

-- 每次请求消耗的令牌数
local permits = tonumber(ARGV[2])

-- 每秒流入桶内的令牌数  即速率
local rate = tonumber(ARGV[3])

-- 当前系统时间
local curr_mill_time = tonumber(ARGV[4])

-- 判断桶key是否存在 存在即拿桶内令牌数量作为当前剩余令牌数 否初始化令牌数为limit
local current_limit = tonumber(redis.call('GET',bucketKey) or limit)

-- 初始化 时间间隔需要放入桶内的令牌数量
local add_token_num = 0

-- 初始化失效时间
local expire_time = 0

-- 获取 上一次请求的时间  如果不存在 即本次为第一次请求 返回0
local last_mill_request_time = tonumber(redis.call('GET',last_mill_request_key) or '0')

-- 计算 按照当前速率最多需要多久能将桶内令牌存满
    expire_time = math.ceil( limit / rate )

-- 如果 last_mill_request_time 为0 则代表本次为第一次请求
--  将当前时间 放入到last_mill_request_key 中
-- 第一次请求  直接返回成功
if last_mill_request_time == 0 then
	redis.call('SET',last_mill_request_key,curr_mill_time)
    -- 设置过期时间为expire_time秒 expire_time秒后key不存在 即代表此时间段无人请求 那么桶内令牌根据流速已经放满 则本key不需要存在 再次请求重新初始化桶内令牌数量即可
	redis.call('EXPIRE',last_mill_request_key,expire_time)
	return 1
-- 如果 last_mill_request_time 不为0 则计算本次请求时间与上次请求时间间隔内 需放入桶内的令牌数
-- 计算公式  （当前时间-上次时间） * 速率 并向下取整
-- 返回 add_token_num
else
	add_token_num = math.floor((curr_mill_time - last_mill_request_time) * rate)
end

--如果当前桶内剩余令牌数 + 应增加令牌数 > 桶内允许的最大令牌数 则将当前桶内剩余数量置为最大数
if current_limit + add_token_num > limit then
	current_limit = limit
-- 否则置为相加的和
else
	current_limit = current_limit + add_token_num
end
    -- 将计算好的桶内应剩余数 放置到bucketKey中
	redis.call('SET',bucketKey,current_limit)


    -- 设置过期时间为expire_time秒 expire_time秒后key不存在 即代表此时间段无人请求 那么桶内令牌根据流速已经放满 则本key不需要存在 再次请求重新初始化桶内令牌数量即可
	redis.call('EXPIRE',bucketKey,expire_time)
-- 判断当前桶内剩余令牌数 - 消耗令牌数是否大于1
-- 小于1 表示桶内令牌不足 返回0 请求失败
if current_limit - permits < 1 then
	return 0
-- 大于1 表示请求通过
else
    -- 重新计算 消耗后的令牌剩余数
	current_limit = current_limit -permits
    -- 放置剩余令牌数到bucketKey中
	redis.call('SET',bucketKey,current_limit)
	-- 根据上面逻辑设置失效时间
	redis.call('EXPIRE',bucketKey,expire_time)
	-- 设置上次请求时间为当前时间
	redis.call('SET',last_mill_request_key,curr_mill_time)
	-- 根据上面逻辑设置失效时间
	redis.call('EXPIRE',last_mill_request_key,expire_time)
	return current_limit
end

