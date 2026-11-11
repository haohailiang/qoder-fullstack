-- 原子性预扣减库存
-- KEYS[1]: 库存 key  seckill:stock:{productId}
-- ARGV[1]: 扣减数量
-- 返回：-1=库存未初始化，-2=库存不足，>=0=扣减成功后的剩余库存
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil then
    return -1
end
local quantity = tonumber(ARGV[1])
if stock < quantity then
    -- 与“扣减后刚好售罄（剩余 0）”区分开，调用方不能把 0 当成失败
    return -2
end
return tonumber(redis.call('DECRBY', KEYS[1], quantity))
