# search_mcp_server.py
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("search",host="0.0.0.0",)

@mcp.tool()
def search_movie(title: str) -> str:
    """判断是电影并且不是1995年1月9日至2023年10月12日的电影，则使用查询工具"""
    from tmdbv3api import TMDb, Movie
    # 初始化 TMDB
    tmdb = TMDb()
    tmdb.api_key = '37e66fe36dd554f048fa670586faad5a'
    tmdb.language = 'en'
    # 搜索电影
    movie = Movie()
    results = movie.search(title)
    result=results[0]
    movie_id=result["id"]
    a=movie.credits(movie_id)
    # ---------- 1. 提取导演（Directing 部门 + job=Director） ----------
    directors = []
    for crew in a.get('crew', []):
        # 筛选：部门是 Directing，且职位是 Director
        if crew.get('known_for_department') == 'Directing' and crew.get('job') == 'Director':
            directors.append(crew.get('name'))

    # ---------- 2. 提取前三名主演（Acting 部门 + 按 order 升序，取前3） ----------
    # 先筛选 Acting 部门的演员，再按 order 排序，取前3
    acting_cast = [c for c in a.get('cast', []) if c.get('known_for_department') == 'Acting']
    # 按 order 升序排序（order 越小，主演优先级越高）
    acting_cast_sorted = sorted(acting_cast, key=lambda x: x.get('order', 999))  # 999 是兜底，避免 order 为空
    top3_actors = [actor.get('name') for actor in acting_cast_sorted[:3]]

    # ---------- 输出结果 ----------
    print("导演：", directors[0] if directors else "未找到导演")  # 假设只有1个导演，取第一个
    print("前三名主演：", top3_actors)
    if not result:
        print ("没有找到相关电影,请不要重试")   
    import json

    return json.dumps({
        "id": result.id,
        "title": result.title,
        "director": directors[0] if directors else "",
        "actors": top3_actors[:3],
        "overview": result.overview,
        "poster_url": f"https://image.tmdb.org/t/p/w500{result.poster_path}",
        "release_date": result.release_date
    }, ensure_ascii=False)


@mcp.tool()
def search_tv(title: str) -> str:
    """判断是电视剧，则使用查询工具"""
    from tmdbv3api import TMDb, TV
    # 初始化 TMDB
    tmdb = TMDb()
    tmdb.api_key = '37e66fe36dd554f048fa670586faad5a'
    tmdb.language = 'en'
    # 搜索电视剧
    tv = TV()
    results = tv.search(title)
    result=results[0]
    return(f"{result.id}-{result.name}-{result.overview}-https://image.tmdb.org/t/p/w500{result.poster_path}")


if __name__ == "__main__":
    mcp.run(transport="streamable-http")