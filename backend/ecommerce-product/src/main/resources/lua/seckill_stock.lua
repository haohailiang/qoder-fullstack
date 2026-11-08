-- 原子性预扣减库存
-- KEYS[1]: 库存 key  seckill:stock:{productId}
-- ARGV[1]: 扣减数量
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil then
    return -1  -- 库存未初始化
end
if stock < tonumber(ARGV[1]) then
    return 0   -- 库存不足
end
redis.call('DECRBY', KEYS[1], ARGV[1])
return stock - tonumber(ARGV[1])  -- 返回剩余库存
