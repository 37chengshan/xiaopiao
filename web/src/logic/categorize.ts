import { type Category } from "../domain/types";

/** Keyword → category rules. First match wins, so order matters. */
const RULES: ReadonlyArray<readonly [Category, RegExp]> = [
  ["SUBSCRIPTION", /会员|订阅|爱奇艺|优酷|腾讯视频|芒果tv|bilibili|b站|网易云|qq音乐|spotify|netflix|icloud|apple music|apple one|chatgpt|自动续费|续费|vip/i],
  ["DRINK", /奶茶|咖啡|瑞幸|星巴克|喜茶|奈雪|蜜雪|古茗|茶百道|霸王茶姬|coco|库迪|果汁|饮料|可乐|拿铁/i],
  ["TRANSPORT", /地铁|公交|打车|滴滴|出租|高铁|火车|机票|共享单车|哈啰|美团单车|加油|停车|高速|taxi|uber|曹操/i],
  ["MEAL", /早饭|早餐|午饭|午餐|晚饭|晚餐|宵夜|夜宵|外卖|美团|饿了么|食堂|火锅|烧烤|面条|拉面|牛肉面|面馆|饭|米粉|餐|包子|豆浆|煎饼|麦当劳|肯德基|kfc|汉堡|便当|盖饭|麻辣烫|小吃/i],
  ["SNACK", /零食|薯片|饼干|糖|巧克力|冰淇淋|雪糕|水果|蛋糕|甜品|面包|辣条|坚果/i],
  ["GROCERY", /超市|买菜|菜场|盒马|叮咚|山姆|沃尔玛|永辉|便利店|全家|罗森|7-?11|日用|纸巾|洗衣|牙膏|洗发/i],
  ["HOUSING", /房租|水费|电费|燃气|煤气|物业|宽带|话费|充值|网费|租金/i],
  ["HEALTH", /医院|药|门诊|挂号|体检|牙科|诊所|健身|瑜伽/i],
  ["SOCIAL", /红包|份子|随礼|请客|礼物|礼金|转账给|aa|AA/],
  ["LEARNING", /书|课程|课|学习|培训|考试|网课|得到|知乎|学费|文具|读书/i],
  ["FUN", /电影|游戏|演唱会|ktv|密室|剧本杀|steam|门票|景区|旅行|酒店|音乐节|桌游/i],
  ["SHOPPING", /淘宝|京东|拼多多|天猫|衣服|鞋|包|购物|商城|优衣库|zara|数码|耳机|化妆|护肤/i],
];

export function categorize(text: string): Category {
  for (const [cat, re] of RULES) {
    if (re.test(text)) return cat;
  }
  return "OTHER";
}

/** Map a payment-app provided classification (e.g. 支付宝「交易分类」) to our category, falling back to text rules. */
export function categoryFromBill(billCategory: string, merchantAndItem: string): Category {
  const byText = categorize(`${merchantAndItem}`);
  if (byText !== "OTHER") return byText;
  const c = billCategory;
  if (/餐饮|美食/.test(c)) return "MEAL";
  if (/交通|出行/.test(c)) return "TRANSPORT";
  if (/日用|超市|商超|生活/.test(c)) return "GROCERY";
  if (/服饰|数码|购物|家居/.test(c)) return "SHOPPING";
  if (/文化|娱乐|休闲/.test(c)) return "FUN";
  if (/医疗|健康/.test(c)) return "HEALTH";
  if (/教育|学习/.test(c)) return "LEARNING";
  if (/红包|转账|人情/.test(c)) return "SOCIAL";
  if (/住房|水电|通讯|话费/.test(c)) return "HOUSING";
  return "OTHER";
}
